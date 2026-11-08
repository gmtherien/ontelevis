package app.ontelevis.core.data.importer

import android.util.Xml
import app.ontelevis.core.data.db.ChannelDao
import app.ontelevis.core.data.db.OntelevisDatabase
import app.ontelevis.core.data.db.PlaylistEntity
import app.ontelevis.core.data.db.ProgrammeDao
import app.ontelevis.core.data.db.ProgrammeEntity
import app.ontelevis.core.data.network.HttpSource
import app.ontelevis.core.parser.xmltv.XmltvParser
import app.ontelevis.core.parser.xtream.XtreamUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import javax.inject.Inject

/**
 * Downloads a playlist's XMLTV guide and replaces its programmes. The guide is parsed as a stream
 * and written in batches inside one transaction; only programmes of the playlist's own channels,
 * from 1 day back to 7 days ahead, are kept. A failure leaves the previous guide in place.
 */
class EpgImporter
    @Inject
    constructor(
        private val http: HttpSource,
        private val database: OntelevisDatabase,
        private val channelDao: ChannelDao,
        private val programmeDao: ProgrammeDao,
    ) {
        /** Guide link of a playlist: the user's, else Xtream's xmltv.php, else the M3U header's. */
        fun epgUrlOf(playlist: PlaylistEntity): String? =
            playlist.epgUrl?.ifBlank { null }
                ?: if (playlist.kind == PlaylistImporter.KIND_XTREAM) {
                    XtreamUrls(playlist.url, playlist.username.orEmpty(), playlist.password.orEmpty()).xmltv()
                } else {
                    playlist.detectedEpgUrl
                }

        /** Returns the number of programmes stored, or null when the playlist has no guide link. */
        suspend fun import(
            playlist: PlaylistEntity,
            now: Long = System.currentTimeMillis(),
        ): Int? =
            withContext(Dispatchers.IO) {
                val url = epgUrlOf(playlist) ?: return@withContext null
                try {
                    val keys = channelDao.epgKeys(playlist.id).toHashSet()
                    if (keys.isEmpty()) return@withContext 0
                    val from = now - KEEP_PAST_MS
                    val to = now + KEEP_FUTURE_MS
                    val parser =
                        XmltvParser {
                            Xml.newPullParser().apply {
                                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
                                // Provider guides often carry a raw "&" or a stray entity: tolerate them
                                // instead of losing the whole guide.
                                runCatching { setFeature(RELAXED, true) }
                            }
                        }
                    http.open(url) { stream ->
                        database.runInTransaction<Int> {
                            programmeDao.deleteForPlaylist(playlist.id)
                            val batch = ArrayList<ProgrammeEntity>(BATCH_SIZE)
                            var count = 0
                            parser.parse(stream) { p ->
                                val key = p.channel.lowercase()
                                if (key in keys && p.stop > from && p.start < to) {
                                    batch +=
                                        ProgrammeEntity(0, playlist.id, key, p.start, p.stop, p.title, p.description)
                                    count++
                                    if (batch.size == BATCH_SIZE) {
                                        programmeDao.insertAll(batch)
                                        batch.clear()
                                    }
                                }
                            }
                            if (batch.isNotEmpty()) programmeDao.insertAll(batch)
                            count
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw e.toRefreshException()
                }
            }

        private companion object {
            const val RELAXED = "http://xmlpull.org/v1/doc/features.html#relaxed"
            const val BATCH_SIZE = 1_000
            const val KEEP_PAST_MS = 24L * 60 * 60 * 1000
            const val KEEP_FUTURE_MS = 7L * 24 * 60 * 60 * 1000
        }
    }
