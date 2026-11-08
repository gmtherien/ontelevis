package app.ontelevis.core.data

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/** A backup file found in Downloads/Ontelevis. */
data class BackupFile(
    val name: String,
    val uri: Uri,
    val modifiedAt: Long,
)

/**
 * Where backups are written and read. Phones use the system file picker (handled by the screen);
 * TV boxes usually have none (Android TV ships a stub that only says "not supported"), so they use
 * the Downloads/Ontelevis folder. Before Android 10 that needs the storage permission.
 */
@Singleton
class BackupFiles
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** True when a real document picker can create and open files. */
        fun hasPicker(): Boolean {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(MIME)
            val handlers = context.packageManager.queryIntentActivities(intent, 0)
            return handlers.any { it.activityInfo.packageName !in STUB_PACKAGES }
        }

        /** Before Android 10 the Downloads folder needs the storage permission. */
        fun needsStoragePermission(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q

        fun suggestedName(now: Long = System.currentTimeMillis()): String =
            "ontelevis-backup-" + SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.ROOT).format(Date(now)) + ".json"

        /** Writes [text] to Downloads/Ontelevis/[name]; returns the folder shown to the user. */
        suspend fun saveToDownloads(
            name: String,
            text: String,
        ): String =
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values =
                        ContentValues().apply {
                            put(MediaStore.Downloads.DISPLAY_NAME, name)
                            put(MediaStore.Downloads.MIME_TYPE, MIME)
                            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
                        }
                    val uri =
                        context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                            ?: error("cannot create $name")
                    context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
                } else {
                    @Suppress("DEPRECATION")
                    val dir =
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
                    dir.mkdirs()
                    File(dir, name).writeText(text)
                }
                "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER"
            }

        /**
         * Backups in Downloads/Ontelevis, newest first. From Android 10 on, only files this
         * installation wrote are visible (Android hides other apps' files, and the app's own after a
         * reinstall).
         */
        suspend fun listDownloads(): List<BackupFile> =
            withContext(Dispatchers.IO) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val found = mutableListOf<BackupFile>()
                    context.contentResolver
                        .query(
                            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                            arrayOf(
                                MediaStore.Downloads._ID,
                                MediaStore.Downloads.DISPLAY_NAME,
                                MediaStore.Downloads.DATE_MODIFIED,
                            ),
                            "${MediaStore.Downloads.RELATIVE_PATH} LIKE ? AND ${MediaStore.Downloads.DISPLAY_NAME} LIKE ?",
                            arrayOf("${Environment.DIRECTORY_DOWNLOADS}/$FOLDER%", "%.json"),
                            "${MediaStore.Downloads.DATE_MODIFIED} DESC",
                        )?.use { c ->
                            while (c.moveToNext()) {
                                found +=
                                    BackupFile(
                                        name = c.getString(1),
                                        uri =
                                            Uri.withAppendedPath(
                                                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                                                c.getLong(0).toString(),
                                            ),
                                        modifiedAt = c.getLong(2) * 1000,
                                    )
                            }
                        }
                    found
                } else {
                    @Suppress("DEPRECATION")
                    val dir =
                        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER)
                    dir
                        .listFiles { f -> f.name.endsWith(".json") }
                        .orEmpty()
                        .sortedByDescending { it.lastModified() }
                        .map { BackupFile(it.name, Uri.fromFile(it), it.lastModified()) }
                }
            }

        suspend fun read(uri: Uri): String =
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)!!.use { it.readBytes().toString(Charsets.UTF_8) }
            }

        suspend fun write(
            uri: Uri,
            text: String,
        ) = withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(text.toByteArray()) }
        }

        companion object {
            const val MIME = "application/json"
            const val FOLDER = "Ontelevis"

            /** "Not supported" placeholders installed instead of a real file picker on Android TV. */
            private val STUB_PACKAGES = setOf("com.android.tv.frameworkpackagestubs")
        }
    }
