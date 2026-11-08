package app.ontelevis.core.data

import android.content.Context
import androidx.room.withTransaction
import app.ontelevis.core.data.db.CategoryLockEntity
import app.ontelevis.core.data.db.FavoriteEntity
import app.ontelevis.core.data.db.GroupPrefEntity
import app.ontelevis.core.data.db.LibraryDao
import app.ontelevis.core.data.db.OntelevisDatabase
import app.ontelevis.core.data.db.PlaylistDao
import app.ontelevis.core.data.db.PlaylistEntity
import app.ontelevis.core.data.db.VodFavoriteEntity
import app.ontelevis.core.data.sync.PlaylistSync
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** What a backup file holds, read before restoring it (shown in the confirmation). */
data class BackupSummary(
    val playlists: List<String>,
    val favorites: Int,
    val createdAt: Long?,
)

/** A backup file that could not be read. */
class BackupFormatException(
    message: String,
) : Exception(message)

/**
 * Backup and restore of the user's setup as a readable JSON file: playlists (with their logins),
 * favourites, category order / hidden / locks, the parental PIN hash, sorts and language.
 * Channels, programmes and titles are not saved: they are downloaded again after a restore.
 *
 * Ids inside the app start with the playlist id (`12::…`), which changes on restore: the file keeps
 * the playlist's position in the list instead (`"playlist": 0`) and the rest of the id.
 */
@Singleton
class BackupManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val database: OntelevisDatabase,
        private val playlistDao: PlaylistDao,
        private val library: LibraryDao,
        private val parental: ParentalControl,
        private val vodPrefs: VodPrefs,
        private val language: LanguageSettings,
    ) {
        private val json = Json { prettyPrint = true }

        suspend fun export(now: Long = System.currentTimeMillis()): String {
            val playlists = playlistDao.all()
            val index = playlists.withIndex().associate { (i, p) -> p.id to i }

            fun ref(id: String): JsonObject? {
                val pid = id.substringBefore("::").toLongOrNull() ?: return null
                val position = index[pid] ?: return null
                return buildJsonObject {
                    put("playlist", position)
                    put("id", id.substringAfter("::"))
                }
            }
            val root =
                buildJsonObject {
                    put("app", "Ontelevis")
                    put("version", FORMAT_VERSION)
                    put("createdAt", now)
                    put(
                        "playlists",
                        buildJsonArray {
                            playlists.forEach { p ->
                                add(
                                    buildJsonObject {
                                        put("name", p.name)
                                        put("kind", p.kind)
                                        put("url", p.url)
                                        p.username?.let { put("username", it) }
                                        p.password?.let { put("password", it) }
                                        p.epgUrl?.let { put("epgUrl", it) }
                                        put("enabled", p.enabled)
                                    },
                                )
                            }
                        },
                    )
                    put("favorites", JsonArray(library.allFavorites().mapNotNull { ref(it.channelId) }))
                    put(
                        "vodFavorites",
                        JsonArray(
                            library.allVodFavorites().mapNotNull { f ->
                                ref(f.contentId)?.let { JsonObject(it + ("kind" to JsonPrimitive(f.kind))) }
                            },
                        ),
                    )
                    put(
                        "categories",
                        JsonArray(
                            library.allGroupPrefs().mapNotNull { g ->
                                val position = index[g.playlistId] ?: return@mapNotNull null
                                buildJsonObject {
                                    put("playlist", position)
                                    put("name", g.name)
                                    g.position?.let { put("position", it) }
                                    put("hidden", g.hidden)
                                }
                            },
                        ),
                    )
                    put(
                        "locks",
                        JsonArray(
                            library.allCategoryLocks().mapNotNull { l ->
                                val position = index[l.playlistId] ?: return@mapNotNull null
                                buildJsonObject {
                                    put("playlist", position)
                                    put("kind", l.kind)
                                    put("name", l.name)
                                }
                            },
                        ),
                    )
                    parental.pinState()?.let { (hash, salt) ->
                        put(
                            "pin",
                            buildJsonObject {
                                put("hash", hash)
                                put("salt", salt)
                            },
                        )
                    }
                    put("sorts", JsonObject(vodPrefs.allSorts().mapValues { JsonPrimitive(it.value) }))
                    put("language", language.current())
                }
            return json.encodeToString(JsonElement.serializer(), root)
        }

        /** Reads [text] without changing anything; throws [BackupFormatException] if it is not a backup. */
        fun summary(text: String): BackupSummary {
            val root = parse(text)
            return BackupSummary(
                playlists = root.array("playlists").map { it.jsonObject.string("name").orEmpty() },
                favorites = root.array("favorites").size + root.array("vodFavorites").size,
                createdAt = root["createdAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull(),
            )
        }

        /**
         * Replaces the whole setup by the backup's, then downloads the playlists again.
         * Returns true when the language changed and the screen must be recreated (before Android 13).
         */
        suspend fun restore(text: String): Boolean {
            val root = parse(text)
            val playlists = root.array("playlists").map { it.jsonObject }
            database.withTransaction {
                // Removing the playlists removes their channels, titles, guide, category settings and locks.
                playlistDao.deleteAll()
                library.clearFavorites()
                library.clearVodFavorites()
                library.clearHistory()
                val ids =
                    playlists.map { p ->
                        playlistDao.insert(
                            PlaylistEntity(
                                name = p.string("name").orEmpty(),
                                kind = p.string("kind") ?: throw BackupFormatException("playlist kind"),
                                url = p.string("url") ?: throw BackupFormatException("playlist url"),
                                username = p.string("username"),
                                password = p.string("password"),
                                epgUrl = p.string("epgUrl"),
                                enabled = p["enabled"]?.jsonPrimitive?.booleanOrNull ?: true,
                            ),
                        )
                    }

                fun idOf(ref: JsonObject): String? {
                    val position = ref["playlist"]?.jsonPrimitive?.intOrNull ?: return null
                    val pid = ids.getOrNull(position) ?: return null
                    return "$pid::" + (ref.string("id") ?: return null)
                }

                fun playlistOf(ref: JsonObject): Long? = ref["playlist"]?.jsonPrimitive?.intOrNull?.let(ids::getOrNull)
                library.insertFavorites(
                    root.array("favorites").mapIndexedNotNull { i, e ->
                        idOf(e.jsonObject)?.let { FavoriteEntity(it, i + 1L) }
                    },
                )
                library.insertVodFavorites(
                    root.array("vodFavorites").mapIndexedNotNull { i, e ->
                        val o = e.jsonObject
                        idOf(o)?.let { VodFavoriteEntity(it, o.string("kind") ?: "movie", i + 1L) }
                    },
                )
                library.upsertGroupPrefs(
                    root.array("categories").mapNotNull { e ->
                        val o = e.jsonObject
                        val pid = playlistOf(o) ?: return@mapNotNull null
                        GroupPrefEntity(
                            playlistId = pid,
                            name = o.string("name") ?: return@mapNotNull null,
                            position = o["position"]?.jsonPrimitive?.intOrNull,
                            hidden = o["hidden"]?.jsonPrimitive?.boolean ?: false,
                        )
                    },
                )
                library.insertCategoryLocks(
                    root.array("locks").mapNotNull { e ->
                        val o = e.jsonObject
                        val pid = playlistOf(o) ?: return@mapNotNull null
                        CategoryLockEntity(
                            pid,
                            o.string("kind") ?: return@mapNotNull null,
                            o.string("name") ?: return@mapNotNull null,
                        )
                    },
                )
            }
            val pin = root["pin"] as? JsonObject
            parental.restorePinState(pin?.let { (it.string("hash") ?: return@let null) to it.string("salt").orEmpty() })
            vodPrefs.restoreSorts(
                (root["sorts"] as? JsonObject)
                    ?.mapNotNull { (k, v) -> v.jsonPrimitive.contentOrNull?.let { k to it } }
                    ?.toMap()
                    .orEmpty(),
            )
            // Channels, titles and guide come back from the providers.
            PlaylistSync.refreshNow(context)
            val tag = root.string("language")
            return if (tag != null && tag != language.current() && (tag.isEmpty() || tag in language.supported)) {
                language.set(tag)
            } else {
                false
            }
        }

        private fun parse(text: String): JsonObject {
            val root =
                runCatching { Json.parseToJsonElement(text).jsonObject }.getOrNull()
                    ?: throw BackupFormatException("not JSON")
            if (root.string("app") != "Ontelevis") throw BackupFormatException("not an Ontelevis backup")
            val version = root["version"]?.jsonPrimitive?.intOrNull ?: 0
            if (version < 1 || version > FORMAT_VERSION) throw BackupFormatException("version $version")
            return root
        }

        private companion object {
            const val FORMAT_VERSION = 1
        }
    }

private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.array(key: String): JsonArray = this[key] as? JsonArray ?: JsonArray(emptyList())
