package app.ontelevis.core.data.importer

import app.ontelevis.core.data.db.EpisodeEntity
import app.ontelevis.core.data.db.MovieEntity
import app.ontelevis.core.data.db.OntelevisDatabase
import app.ontelevis.core.data.db.PlaylistEntity
import app.ontelevis.core.data.db.SeriesEntity
import app.ontelevis.core.data.db.VOD_MOVIE
import app.ontelevis.core.data.db.VOD_SERIES
import app.ontelevis.core.data.db.VodCategoryEntity
import app.ontelevis.core.data.db.VodDao
import app.ontelevis.core.data.network.HttpSource
import app.ontelevis.core.model.searchKeyOf
import app.ontelevis.core.parser.ImportedEpisode
import app.ontelevis.core.parser.ImportedMovie
import app.ontelevis.core.parser.ImportedSeries
import app.ontelevis.core.parser.M3uClassifier
import app.ontelevis.core.parser.M3uKind
import app.ontelevis.core.parser.VodImport
import app.ontelevis.core.parser.m3u.M3uEntry
import app.ontelevis.core.parser.xtream.XtreamJson
import app.ontelevis.core.parser.xtream.XtreamUrls
import app.ontelevis.core.parser.xtream.XtreamVodInfo
import app.ontelevis.core.parser.xtream.XtreamVodJson
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

/** Ids are prefixed per kind: Xtream numbers movies and episodes separately, they can collide. */
internal fun movieId(
    playlistId: Long,
    rawId: String,
) = "$playlistId::m:$rawId"

internal fun seriesId(
    playlistId: Long,
    rawId: String,
) = "$playlistId::s:$rawId"

internal fun episodeId(
    playlistId: Long,
    rawId: String,
) = "$playlistId::e:$rawId"

/**
 * Writes one playlist's movies and series in batches. Call inside a transaction: it first clears
 * what it replaces, so a failure rolls everything back to the previous content.
 */
internal class VodWriter(
    private val dao: VodDao,
    private val playlistId: Long,
) {
    private val movieBatch = ArrayList<MovieEntity>(BATCH_SIZE)
    private val seriesBatch = ArrayList<SeriesEntity>(BATCH_SIZE)
    private val episodeBatch = ArrayList<EpisodeEntity>(BATCH_SIZE)
    private val movieGroups = LinkedHashSet<String>()
    private val seriesGroups = LinkedHashSet<String>()

    // M3U series are built from their episodes.
    private val m3uSeries = LinkedHashMap<String, SeriesEntity>()
    private val m3uEpisodeCount = HashMap<String, Int>()

    var movies = 0
        private set
    var series = 0
        private set

    fun clearMovies() {
        dao.deleteMovies(playlistId)
        dao.deleteCategories(playlistId, VOD_MOVIE)
    }

    fun clearSeries() {
        dao.deleteEpisodes(playlistId)
        dao.deleteSeries(playlistId)
        dao.deleteCategories(playlistId, VOD_SERIES)
    }

    fun addMovie(movie: ImportedMovie) {
        movieGroups += movie.group
        movieBatch +=
            MovieEntity(
                id = movieId(playlistId, movie.rawId),
                playlistId = playlistId,
                rawId = movie.rawId,
                name = movie.name,
                groupName = movie.group,
                streamUrl = movie.streamUrl,
                posterUrl = movie.posterUrl,
                rating = movie.rating,
                year = movie.year,
                added = movie.added,
                sortOrder = movies++,
                searchKey = searchKeyOf(movie.name),
            )
        if (movieBatch.size == BATCH_SIZE) flushMovies()
    }

    fun addSeries(item: ImportedSeries) {
        seriesGroups += item.group
        seriesBatch += item.toEntity(sortOrder = series++)
        if (seriesBatch.size == BATCH_SIZE) flushSeries()
    }

    fun addM3uEpisode(
        entry: M3uEntry,
        kind: M3uKind.Episode,
    ) {
        val key = M3uClassifier.seriesKey(kind.series)
        val index = m3uEpisodeCount.merge(key, 1, Int::plus) ?: 1
        val episode = M3uClassifier.episode(entry, kind, index)
        m3uSeries.getOrPut(key) {
            seriesGroups += entry.group.orEmpty()
            ImportedSeries(
                rawId = key,
                name = kind.series,
                group = entry.group.orEmpty(),
                posterUrl = entry.logo,
                backdropUrl = null,
                plot = null,
                genre = null,
                year = null,
                rating = 0.0,
            ).toEntity(sortOrder = series++)
        }
        addEpisode(episode)
    }

    fun addEpisode(episode: ImportedEpisode) {
        episodeBatch += episode.toEntity()
        if (episodeBatch.size == BATCH_SIZE) flushEpisodes()
    }

    /** Writes what is left. [providerOrder] keeps the panel's category order when known. */
    fun finish(
        movieOrder: List<String> = emptyList(),
        seriesOrder: List<String> = emptyList(),
    ) {
        flushMovies()
        seriesBatch += m3uSeries.values
        m3uSeries.clear()
        flushSeries()
        flushEpisodes()
        dao.insertCategories(categories(VOD_MOVIE, movieGroups, movieOrder))
        dao.insertCategories(categories(VOD_SERIES, seriesGroups, seriesOrder))
    }

    private fun categories(
        kind: String,
        used: Set<String>,
        order: List<String>,
    ): List<VodCategoryEntity> {
        val rank = order.withIndex().associate { (index, name) -> name to index }
        val firstSeen = used.withIndex().associate { (index, name) -> name to index }
        return used
            .sortedWith(compareBy({ rank[it] ?: Int.MAX_VALUE }, { firstSeen[it] }))
            .mapIndexed { index, name -> VodCategoryEntity(playlistId, kind, name, index) }
    }

    private fun flushMovies() {
        if (movieBatch.isNotEmpty()) dao.insertMovies(movieBatch)
        movieBatch.clear()
    }

    private fun flushSeries() {
        if (seriesBatch.isNotEmpty()) dao.insertSeries(seriesBatch)
        seriesBatch.clear()
    }

    private fun flushEpisodes() {
        if (episodeBatch.isNotEmpty()) dao.insertEpisodes(episodeBatch)
        episodeBatch.clear()
    }

    private fun ImportedSeries.toEntity(sortOrder: Int) =
        SeriesEntity(
            id = seriesId(playlistId, rawId),
            playlistId = playlistId,
            rawId = rawId,
            name = name,
            groupName = group,
            posterUrl = posterUrl,
            backdropUrl = backdropUrl,
            plot = plot,
            genre = genre,
            year = year,
            rating = rating,
            sortOrder = sortOrder,
            searchKey = searchKeyOf(name),
        )

    private fun ImportedEpisode.toEntity() =
        EpisodeEntity(
            id = episodeId(playlistId, rawId),
            seriesId = seriesId(playlistId, seriesRawId),
            playlistId = playlistId,
            season = season,
            episode = episode,
            title = title,
            streamUrl = streamUrl,
            plot = plot,
            imageUrl = imageUrl,
            durationSecs = durationSecs,
        )

    private companion object {
        const val BATCH_SIZE = 500
    }
}

/**
 * Xtream movies and series. Big panels list hundreds of thousands of titles, so this runs in the
 * background after the channels, streams the replies and writes in batches. Episodes are fetched
 * per series when its page opens ([loadEpisodes]).
 */
class VodImporter
    @Inject
    constructor(
        private val http: HttpSource,
        private val database: OntelevisDatabase,
        private val vodDao: VodDao,
    ) {
        /** Movie and series counts, or null for a playlist without a VOD API (M3U). */
        suspend fun import(playlist: PlaylistEntity): Pair<Int, Int>? =
            withContext(Dispatchers.IO) {
                if (playlist.kind != PlaylistImporter.KIND_XTREAM) return@withContext null
                guarded {
                    val urls = playlist.urls()
                    val movieCategories =
                        http.open(
                            urls.playerApi("get_vod_categories"),
                        ) { XtreamJson.decodeCategories(it) }
                    val movies =
                        http.open(urls.playerApi("get_vod_streams")) { stream ->
                            database.runInTransaction<Int> {
                                val writer = VodWriter(vodDao, playlist.id).apply { clearMovies() }
                                VodImport
                                    .fromXtreamMovies(XtreamVodJson.decodeVodStreams(stream), movieCategories, urls)
                                    .forEach(writer::addMovie)
                                writer.finish(movieOrder = movieCategories.mapNotNull { it.categoryName })
                                writer.movies
                            }
                        }
                    val seriesCategories =
                        http.open(urls.playerApi("get_series_categories")) { XtreamJson.decodeCategories(it) }
                    val series =
                        http.open(urls.playerApi("get_series")) { stream ->
                            database.runInTransaction<Int> {
                                val writer = VodWriter(vodDao, playlist.id).apply { clearSeries() }
                                VodImport
                                    .fromXtreamSeries(XtreamVodJson.decodeSeries(stream), seriesCategories)
                                    .forEach(writer::addSeries)
                                writer.finish(seriesOrder = seriesCategories.mapNotNull { it.categoryName })
                                writer.series
                            }
                        }
                    movies to series
                }
            }

        /** Downloads the episodes of one Xtream series and stores them. */
        suspend fun loadEpisodes(
            playlist: PlaylistEntity,
            series: SeriesEntity,
        ) = withContext(Dispatchers.IO) {
            if (playlist.kind != PlaylistImporter.KIND_XTREAM) return@withContext
            guarded {
                val urls = playlist.urls()
                val url = urls.playerApi("get_series_info") + "&series_id=" + series.rawId
                val episodes = http.open(url) { XtreamVodJson.decodeEpisodes(it) }
                val writer = VodWriter(vodDao, playlist.id)
                val entities = VodImport.fromXtreamEpisodes(series.rawId, episodes, urls)
                database.runInTransaction {
                    vodDao.deleteEpisodesOf(series.id)
                    entities.forEach(writer::addEpisode)
                    writer.finish()
                }
            }
        }

        /** Plot, cast, duration and backdrop of one Xtream movie. */
        suspend fun movieInfo(
            playlist: PlaylistEntity,
            movie: MovieEntity,
        ): XtreamVodInfo? =
            withContext(Dispatchers.IO) {
                if (playlist.kind != PlaylistImporter.KIND_XTREAM) return@withContext null
                guarded {
                    val url = playlist.urls().playerApi("get_vod_info") + "&vod_id=" + movie.rawId
                    http.open(url) { XtreamVodJson.decodeVodInfo(it) }
                }
            }

        private fun PlaylistEntity.urls() = XtreamUrls(url, username.orEmpty(), password.orEmpty())

        private inline fun <T> guarded(block: () -> T): T =
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                throw e.toRefreshException()
            }
    }
