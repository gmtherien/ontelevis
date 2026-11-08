package app.ontelevis.core.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import app.ontelevis.core.model.AppVersion
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

/** A newer release found on GitHub. */
data class AvailableUpdate(
    val version: AppVersion,
    /** Release notes as written on GitHub (markdown), possibly empty. */
    val notes: String,
    val downloadUrl: String,
    /** Size of the APK in bytes, 0 when unknown. */
    val size: Long,
)

/** Why an update could not be prepared. */
enum class UpdateError {
    /** No connection or GitHub did not answer. */
    NETWORK,

    /** Not enough free space for the download. */
    STORAGE,

    /** The downloaded file is not a newer Ontelevis signed with the same key. */
    INVALID,
}

class UpdateException(
    val error: UpdateError,
) : Exception(error.name)

/**
 * Checks GitHub releases for a newer Ontelevis and installs it through the system installer
 * (Android does not let an app update itself silently: the user confirms once).
 * Repo and asset name must match `.github/workflows/release.yml`.
 */
@Singleton
class AppUpdater
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val http: OkHttpClient,
        private val prefs: UpdatePrefs,
    ) {
        private val packageInfo: PackageInfo get() =
            context.packageManager.getPackageInfo(
                context.packageName,
                signingFlags(),
            )

        /** Version of the app running now. */
        val installedVersionName: String get() = packageInfo.versionName.orEmpty()

        /** Development builds (`app.ontelevis.debug`) never update: they would install the release app beside them. */
        val canUpdate: Boolean
            get() = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0

        /** The newest release worth offering, or null when up to date. Throws [UpdateException] (NETWORK). */
        suspend fun check(): AvailableUpdate? {
            if (!canUpdate) return null
            val installed = AppVersion.parse(installedVersionName) ?: return null
            val body =
                withContext(Dispatchers.IO) {
                    val request =
                        Request
                            .Builder()
                            .url(RELEASES_URL)
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", "Ontelevis/$installedVersionName")
                            .build()
                    runCatching {
                        http.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) throw UpdateException(UpdateError.NETWORK)
                            response.body.string()
                        }
                    }.getOrElse { throw UpdateException(UpdateError.NETWORK) }
                }
            return pickUpdate(body, installed, prefs.betas.value)
        }

        /**
         * Downloads [update] and checks it is a newer Ontelevis signed with the same key as this one.
         * [onProgress] gets 0..1 (or -1 when the size is unknown).
         */
        suspend fun download(
            update: AvailableUpdate,
            onProgress: (Float) -> Unit,
        ): File =
            withContext(Dispatchers.IO) {
                val dir = File(context.cacheDir, UPDATE_DIR).apply { mkdirs() }
                dir.listFiles()?.forEach { it.delete() }
                if (update.size > 0 && dir.usableSpace < update.size * 2) throw UpdateException(UpdateError.STORAGE)
                val file = File(dir, "ontelevis-${update.version}.apk")
                try {
                    val request =
                        Request
                            .Builder()
                            .url(update.downloadUrl)
                            .header("User-Agent", "Ontelevis/$installedVersionName")
                            .build()
                    http.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw UpdateException(UpdateError.NETWORK)
                        val total = response.body.contentLength().takeIf { it > 0 } ?: update.size
                        response.body.byteStream().use { input ->
                            file.outputStream().use { output ->
                                val buffer = ByteArray(64 * 1024)
                                var done = 0L
                                while (true) {
                                    coroutineContext.ensureActive()
                                    val n = input.read(buffer)
                                    if (n < 0) break
                                    output.write(buffer, 0, n)
                                    done += n
                                    onProgress(if (total > 0) (done.toFloat() / total).coerceAtMost(1f) else -1f)
                                }
                            }
                        }
                    }
                    if (update.size > 0 && file.length() != update.size) throw UpdateException(UpdateError.NETWORK)
                    verify(file)
                    file
                } catch (e: UpdateException) {
                    file.delete()
                    throw e
                } catch (e: java.io.IOException) {
                    file.delete()
                    throw UpdateException(UpdateError.NETWORK)
                }
            }

        /**
         * Opens the system installer on [file]. When the app may not install apps yet, opens the
         * system screen to allow it instead and returns false (the user comes back and retries).
         */
        fun install(file: File): Boolean {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
                val intent =
                    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                // Fire TV has no such screen: the user is told where the setting is.
                runCatching { context.startActivity(intent) }
                return false
            }
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", file)
            val intent =
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, APK_MIME)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            return try {
                context.startActivity(intent)
                true
            } catch (e: ActivityNotFoundException) {
                false
            }
        }

        /** Downloads left by an earlier update are no longer needed. */
        fun cleanUp() {
            File(context.cacheDir, UPDATE_DIR).listFiles()?.forEach { it.delete() }
        }

        private fun verify(file: File) {
            val pm = context.packageManager
            val archive =
                pm.getPackageArchiveInfo(file.path, signingFlags()) ?: throw UpdateException(UpdateError.INVALID)
            val installed = packageInfo
            val sameApp = archive.packageName == context.packageName
            val newer = PackageInfoCompat.getLongVersionCode(archive) > PackageInfoCompat.getLongVersionCode(installed)
            if (!sameApp || !newer || signers(archive) != signers(installed) || signers(installed).isEmpty()) {
                throw UpdateException(UpdateError.INVALID)
            }
        }

        @Suppress("DEPRECATION")
        private fun signers(info: PackageInfo): Set<String> {
            val signatures =
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    info.signingInfo?.apkContentsSigners
                } else {
                    info.signatures
                }
            return signatures
                .orEmpty()
                .map { sig ->
                    MessageDigest
                        .getInstance(
                            "SHA-256",
                        ).digest(sig.toByteArray())
                        .joinToString("") { "%02x".format(it) }
                }.toSet()
        }

        @Suppress("DEPRECATION")
        private fun signingFlags(): Int =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES
            } else {
                PackageManager.GET_SIGNATURES
            }

        companion object {
            const val REPO = "gmtherien/ontelevis"
            const val ASSET_NAME = "ontelevis.apk"
            private const val RELEASES_URL = "https://api.github.com/repos/$REPO/releases?per_page=20"
            private const val UPDATE_DIR = "updates"
            private const val APK_MIME = "application/vnd.android.package-archive"

            /**
             * From the GitHub releases list ([json]): the highest version above [installed] that has the
             * APK asset, skipping drafts, and pre-releases unless [betas].
             */
            fun pickUpdate(
                json: String,
                installed: AppVersion,
                betas: Boolean,
            ): AvailableUpdate? {
                val releases = runCatching { Json.parseToJsonElement(json) as? JsonArray }.getOrNull() ?: return null
                return releases
                    .mapNotNull { element ->
                        val release = element as? JsonObject ?: return@mapNotNull null
                        if (release.bool("draft")) return@mapNotNull null
                        val version = AppVersion.parse(release.string("tag_name")) ?: return@mapNotNull null
                        if ((release.bool("prerelease") || version.isPreRelease) && !betas) return@mapNotNull null
                        val asset =
                            (release["assets"] as? JsonArray)
                                ?.mapNotNull { it as? JsonObject }
                                ?.firstOrNull { it.string("name") == ASSET_NAME }
                                ?: return@mapNotNull null
                        AvailableUpdate(
                            version = version,
                            notes = release.string("body").orEmpty(),
                            downloadUrl = asset.string("browser_download_url") ?: return@mapNotNull null,
                            size = (asset["size"] as? JsonPrimitive)?.longOrNull ?: 0,
                        )
                    }.filter { it.version > installed }
                    .maxByOrNull { it.version }
            }

            private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

            private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.booleanOrNull ?: false
        }
    }
