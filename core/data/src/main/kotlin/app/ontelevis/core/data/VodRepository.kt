package app.ontelevis.core.data

import app.ontelevis.core.data.db.EpisodeEntity
import app.ontelevis.core.data.db.LibraryDao
import app.ontelevis.core.data.db.MovieEntity
import app.ontelevis.core.data.db.PlaylistDao
import app.ontelevis.core.data.db.SeriesEntity
import app.ontelevis.core.data.db.VOD_MOVIE
import app.ontelevis.core.data.db.VOD_SERIES
import app.ontelevis.core.data.db.VodDao
import app.ontelevis.core.data.db.WatchProgressEntity
import app.ontelevis.core.data.importer.RefreshException
import app.ontelevis.core.data.importer.VodImporter
import app.ontelevis.core.model.Episode
import app.ontelevis.core.model.Movie
import app.ontelevis.core.model.MovieDetails
import app.ontelevis.core.model.RefreshError
import app.ontelevis.core.model.Series
import app.ontelevis.core.model.VodCategory
import app.ontelevis.core.model.VodKind
import app.ontelevis.core.model.WatchProgress
import app.ontelevis.core.model.searchKeyOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import javax.inject.Inject

interface VodRepository {
    fun observeCategories(kind: VodKind): Flow<List<VodCategory>>

    fun observeMovies(category: VodCategory): Flow<List<Movie>>

    /** Favourite movies or series, in the order they were added. */
    fun observeFavoriteMovies(): Flow<List<Movie>>

    fun observeFavoriteSeries(): Flow<List<Series>>

    fun observeFavoriteIds(): Flow<Set<String>>

    /** Movies or series of the enabled playlists whose name contains [query] (accents and case ignored). */
    suspend fun searchMovies(
        query: String,
        limit: Int = SEARCH_LIMIT,
    ): List<Movie>

    suspend fun searchSeries(
        query: String,
        limit: Int = SEARCH_LIMIT,
    ): List<Series>

    /** Adds or removes a movie / series from the favourites; returns true when it is now a favourite. */
    suspend fun toggleFavorite(
        contentId: String,
        kind: VodKind,
    ): Boolean

    fun observeSeries(category: VodCategory): Flow<List<Series>>

    suspend fun movie(id: String): Movie?

    suspend fun series(id: String): Series?

    suspend fun episode(id: String): Episode?

    /** Plot, cast, duration... fetched from the provider (null for M3U or on failure). */
    suspend fun movieDetails(id: String): MovieDetails?

    fun observeEpisodes(seriesId: String): Flow<List<Episode>>

    /** Fetches a series' episodes from the provider (Xtream). Returns the error, or null. */
    suspend fun refreshEpisodes(seriesId: String): RefreshError?

    fun observeProgress(contentIds: List<String>): Flow<Map<String, WatchProgress>>

    suspend fun progress(contentId: String): WatchProgress?

    suspend fun saveProgress(progress: WatchProgress)
}

internal class RoomVodRepository
    @Inject
    constructor(
        private val dao: VodDao,
        private val playlists: PlaylistDao,
        private val importer: VodImporter,
        private val library: LibraryDao,
    ) : VodRepository {
        override fun observeCategories(kind: VodKind): Flow<List<VodCategory>> =
            dao.observeCategories(kind.key).map { rows ->
                rows.map { VodCategory(it.playlistId, kind, it.name, it.count) }
            }

        override fun observeMovies(category: VodCategory): Flow<List<Movie>> =
            dao.observeMovies(category.playlistId, category.name).map { rows -> rows.map { it.toModel() } }

        override fun observeSeries(category: VodCategory): Flow<List<Series>> =
            dao.observeSeries(category.playlistId, category.name).map { rows -> rows.map { it.toModel() } }

        override fun observeFavoriteMovies(): Flow<List<Movie>> =
            library.observeFavoriteMovies().map { rows -> rows.map { it.toModel() } }

        override fun observeFavoriteSeries(): Flow<List<Series>> =
            library.observeFavoriteSeries().map { rows -> rows.map { it.toModel() } }

        override fun observeFavoriteIds(): Flow<Set<String>> = library.observeVodFavoriteIds().map { it.toSet() }

        override suspend fun searchMovies(
            query: String,
            limit: Int,
        ): List<Movie> =
            dao.searchMovies(likePattern(searchKeyOf(query)), likePattern(query.trim()), limit).map {
                it.toModel()
            }

        override suspend fun searchSeries(
            query: String,
            limit: Int,
        ): List<Series> =
            dao.searchSeries(likePattern(searchKeyOf(query)), likePattern(query.trim()), limit).map {
                it.toModel()
            }

        override suspend fun toggleFavorite(
            contentId: String,
            kind: VodKind,
        ): Boolean =
            if (library.isVodFavorite(contentId)) {
                library.removeVodFavorite(contentId)
                false
            } else {
                library.addVodFavorite(contentId, kind.key)
                true
            }

        override suspend fun movie(id: String): Movie? = dao.movie(id)?.toModel()

        override suspend fun series(id: String): Series? = dao.series(id)?.toModel()

        override suspend fun episode(id: String): Episode? = dao.episode(id)?.toModel()

        override suspend fun movieDetails(id: String): MovieDetails? {
            val movie = dao.movie(id) ?: return null
            val playlist = playlists.get(movie.playlistId) ?: return null
            val info = runCatching { importer.movieInfo(playlist, movie) }.getOrNull() ?: return null
            return MovieDetails(
                plot = info.plot,
                cast = info.cast,
                director = info.director,
                genre = info.genre,
                durationSecs = info.durationSecs,
                backdropUrl = info.backdropPath ?: info.backdrop,
            )
        }

        override fun observeEpisodes(seriesId: String): Flow<List<Episode>> =
            dao.observeEpisodes(seriesId).map { rows -> rows.map { it.toModel() } }

        override suspend fun refreshEpisodes(seriesId: String): RefreshError? {
            val series = dao.series(seriesId) ?: return null
            val playlist = playlists.get(series.playlistId) ?: return null
            return try {
                importer.loadEpisodes(playlist, series)
                null
            } catch (e: RefreshException) {
                e.reason
            }
        }

        override fun observeProgress(contentIds: List<String>): Flow<Map<String, WatchProgress>> {
            if (contentIds.isEmpty()) return flowOf(emptyMap())
            // SQLite allows a limited number of arguments: episodes lists stay far below it.
            return dao.observeProgress(contentIds.take(MAX_IDS)).map { rows ->
                rows.associate {
                    it.contentId to
                        it.toModel()
                }
            }
        }

        override suspend fun progress(contentId: String): WatchProgress? = dao.progress(contentId)?.toModel()

        override suspend fun saveProgress(progress: WatchProgress) =
            dao.saveProgress(
                WatchProgressEntity(
                    progress.contentId,
                    progress.positionMs,
                    progress.durationMs,
                    System.currentTimeMillis(),
                ),
            )

        private companion object {
            const val MAX_IDS = 900
        }
    }

private val VodKind.key: String get() = if (this == VodKind.MOVIE) VOD_MOVIE else VOD_SERIES

/** Search results are capped: a two-letter search on a big panel matches tens of thousands of titles. */
const val SEARCH_LIMIT = 500

/** `%text%` for LIKE, with LIKE's own wildcards in the text escaped. */
private fun likePattern(text: String): String =
    "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%"

private fun MovieEntity.toModel() = Movie(id, playlistId, name, groupName, streamUrl, posterUrl, rating, year)

private fun SeriesEntity.toModel() =
    Series(id, playlistId, name, groupName, posterUrl, backdropUrl, plot, genre, year, rating)

private fun EpisodeEntity.toModel() =
    Episode(id, seriesId, season, episode, title, streamUrl, plot, imageUrl, durationSecs)

private fun WatchProgressEntity.toModel() = WatchProgress(contentId, positionMs, durationMs)
