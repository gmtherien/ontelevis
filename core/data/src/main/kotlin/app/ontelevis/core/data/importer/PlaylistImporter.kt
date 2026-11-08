package app.ontelevis.core.data.importer

import app.ontelevis.core.data.db.ChannelDao
import app.ontelevis.core.data.db.ChannelEntity
import app.ontelevis.core.data.db.ChannelGroupDao
import app.ontelevis.core.data.db.ChannelGroupEntity
import app.ontelevis.core.data.db.OntelevisDatabase
import app.ontelevis.core.data.db.PlaylistEntity
import app.ontelevis.core.data.db.VodDao
import app.ontelevis.core.data.network.HttpSource
import app.ontelevis.core.data.network.HttpStatusException
import app.ontelevis.core.model.RefreshError
import app.ontelevis.core.parser.ChannelImport
import app.ontelevis.core.parser.ImportedChannel
import app.ontelevis.core.parser.M3uClassifier
import app.ontelevis.core.parser.M3uKind
import app.ontelevis.core.parser.m3u.M3uParser
import app.ontelevis.core.parser.xtream.XtreamJson
import app.ontelevis.core.parser.xtream.XtreamUrls
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import java.io.IOException
import javax.inject.Inject

class RefreshException(
    val reason: RefreshError,
    cause: Throwable? = null,
) : RuntimeException(reason.name, cause)

/** Finds the cause that explains a failure, even when Room re-wraps it inside the transaction. */
internal fun Throwable.toRefreshException(): RefreshException {
    for (e in generateSequence(this) { it.cause }) {
        when (e) {
            is RefreshException -> return e
            is HttpStatusException -> return RefreshException(RefreshError.HTTP, e)
            is IOException -> return RefreshException(RefreshError.NETWORK, e)
            is SerializationException -> return RefreshException(RefreshError.FORMAT, e)
        }
    }
    // Bad URL, a JSON value of the wrong shape, or anything else unexpected in the reply.
    return RefreshException(RefreshError.FORMAT, this)
}

/**
 * Downloads a playlist and replaces its channels. The reply is parsed as a stream and written in
 * batches inside one transaction: a failure (or an empty reply) leaves the previous channels intact.
 */
class PlaylistImporter
    @Inject
    constructor(
        private val http: HttpSource,
        private val database: OntelevisDatabase,
        private val channelDao: ChannelDao,
        private val groupDao: ChannelGroupDao,
        private val vodDao: VodDao,
    ) {
        /** Channels stored, and the guide link an M3U header announced (null for Xtream). */
        data class Result(
            val channelCount: Int,
            val detectedEpgUrl: String?,
            /** Movies and series found in an M3U (null for Xtream: its VOD loads separately). */
            val movieCount: Int? = null,
            val seriesCount: Int? = null,
        )

        suspend fun import(playlist: PlaylistEntity): Result =
            withContext(Dispatchers.IO) {
                try {
                    when (playlist.kind) {
                        KIND_XTREAM -> Result(importXtream(playlist), null)
                        else -> importM3u(playlist)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    throw e.toRefreshException()
                }
            }

        /** One pass over the M3U: live entries become channels, the others movies and series. */
        private fun importM3u(playlist: PlaylistEntity): Result =
            http.open(playlist.url) { stream ->
                var header = emptyMap<String, String>()
                val entries = M3uParser.parse(stream.reader(Charsets.UTF_8)) { header = it }
                database.runInTransaction<Result> {
                    val vod = VodWriter(vodDao, playlist.id).apply { clearMovies() }.also { it.clearSeries() }
                    val live =
                        entries.filter { entry ->
                            when (val kind = M3uClassifier.kind(entry)) {
                                M3uKind.Live -> {
                                    true
                                }

                                M3uKind.Movie -> {
                                    vod.addMovie(M3uClassifier.movie(entry))
                                    false
                                }

                                is M3uKind.Episode -> {
                                    vod.addM3uEpisode(entry, kind)
                                    false
                                }
                            }
                        }
                    val count =
                        replaceChannels(playlist.id, ChannelImport.fromM3u(live)) { vod.movies + vod.series > 0 }
                    vod.finish()
                    Result(count, M3uParser.epgUrlOf(header), vod.movies, vod.series)
                }
            }

        private fun importXtream(playlist: PlaylistEntity): Int {
            val urls = XtreamUrls(playlist.url, playlist.username.orEmpty(), playlist.password.orEmpty())
            val account = http.open(urls.playerApi()) { XtreamJson.decodeAccount(it) }
            if (account.userInfo?.auth != 1) throw RefreshException(RefreshError.AUTH)
            val categories = http.open(urls.playerApi("get_live_categories")) { XtreamJson.decodeCategories(it) }
            return http.open(urls.playerApi("get_live_streams")) { stream ->
                replaceChannels(
                    playlist.id,
                    ChannelImport.fromXtream(XtreamJson.decodeLiveStreams(stream), categories, urls),
                    // Categories keep the panel's own order (the one TiviMate shows), not first-seen order.
                    providerGroupOrder = categories.mapNotNull { it.categoryName },
                )
            }
        }

        private fun replaceChannels(
            playlistId: Long,
            channels: Sequence<ImportedChannel>,
            providerGroupOrder: List<String> = emptyList(),
            /** True when something else than channels was found (an M3U with only movies is not empty). */
            hasOtherContent: () -> Boolean = { false },
        ): Int =
            database.runInTransaction<Int> {
                // Keep the user's hidden groups across refreshes (manual order comes with V1 step 5).
                val previousGroups = groupDao.groupsOf(playlistId).associateBy { it.name }
                channelDao.deleteForPlaylist(playlistId)
                groupDao.deleteForPlaylist(playlistId)

                val groupOrder = LinkedHashMap<String, Int>()
                val batch = ArrayList<ChannelEntity>(BATCH_SIZE)
                var count = 0
                for (channel in channels) {
                    groupOrder.getOrPut(channel.group) { groupOrder.size }
                    batch += channel.toEntity(playlistId, sortOrder = count++)
                    if (batch.size == BATCH_SIZE) {
                        channelDao.insertAll(batch)
                        batch.clear()
                    }
                }
                if (batch.isNotEmpty()) channelDao.insertAll(batch)
                // Throwing here rolls the transaction back: the old channels stay.
                if (count == 0 && !hasOtherContent()) throw RefreshException(RefreshError.EMPTY)

                val rank = providerGroupOrder.withIndex().associate { (index, name) -> name to index }
                val ordered = groupOrder.keys.sortedWith(compareBy({ rank[it] ?: Int.MAX_VALUE }, { groupOrder[it] }))
                groupDao.insertAll(
                    ordered.mapIndexed { order, name ->
                        previousGroups[name]?.copy(sortOrder = order)
                            ?: ChannelGroupEntity(playlistId, name, sortOrder = order)
                    },
                )
                count
            }

        private fun ImportedChannel.toEntity(
            playlistId: Long,
            sortOrder: Int,
        ) = ChannelEntity(
            id = "$playlistId::$rawId",
            playlistId = playlistId,
            rawId = rawId,
            number = number,
            name = name,
            groupName = group,
            streamUrl = streamUrl,
            logoUrl = logoUrl,
            epgId = epgId,
            catchupDays = catchupDays,
            sortOrder = sortOrder,
        )

        companion object {
            const val KIND_M3U = "m3u"
            const val KIND_XTREAM = "xtream"
            private const val BATCH_SIZE = 500
        }
    }
