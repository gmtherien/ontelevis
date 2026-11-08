package app.ontelevis.feature.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.data.VodRepository
import app.ontelevis.core.model.Episode
import app.ontelevis.core.model.Movie
import app.ontelevis.core.model.MovieDetails
import app.ontelevis.core.model.Playlist
import app.ontelevis.core.model.RefreshError
import app.ontelevis.core.model.Series
import app.ontelevis.core.model.VodCategory
import app.ontelevis.core.model.VodKind
import app.ontelevis.core.model.WatchProgress
import app.ontelevis.core.player.VodPlayer
import app.ontelevis.core.player.VodState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** A poster of the grid: a movie or a series, with its resume progress (movies). */
data class VodItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double,
    val progress: Float?,
)

/** The posters of one category; [categoryKey] tells which, so focus waits for the right list. */
data class VodItems(
    val categoryKey: Pair<Long, String>? = null,
    val list: List<VodItem> = emptyList(),
)

data class VodBrowseState(
    /** Null while loading. */
    val playlists: List<Playlist>? = null,
    val categories: List<VodCategory> = emptyList(),
    val selected: VodCategory? = null,
)

/** Movies or series grid with its categories. The kind is set by the screen ([setKind]). */
@HiltViewModel
class VodBrowseViewModel
    @Inject
    constructor(
        playlists: PlaylistRepository,
        private val vod: VodRepository,
    ) : ViewModel() {
        private val kind = MutableStateFlow<VodKind?>(null)
        private val selectedKey = MutableStateFlow<Pair<Long, String>?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val categories: Flow<List<VodCategory>> =
            kind.flatMapLatest { k -> if (k == null) flowOf(emptyList()) else vod.observeCategories(k) }

        val state: StateFlow<VodBrowseState> =
            combine(playlists.observePlaylists(), categories, selectedKey) { lists, cats, key ->
                VodBrowseState(lists, cats, cats.firstOrNull { it.key == key } ?: cats.firstOrNull())
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VodBrowseState())

        @OptIn(ExperimentalCoroutinesApi::class)
        val items: StateFlow<VodItems> =
            state
                .map { it.selected }
                .distinctUntilChangedBy { it?.key to it?.kind }
                .flatMapLatest { category ->
                    when (category?.kind) {
                        null -> flowOf(emptyList())
                        VodKind.MOVIE -> moviesWithProgress(category)
                        VodKind.SERIES -> vod.observeSeries(category).map { list -> list.map { it.toItem() } }
                    }.map { VodItems(category?.key, it) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VodItems())

        fun setKind(value: VodKind) {
            if (kind.value != value) kind.value = value
        }

        /** Last poster opened: Back from its page puts the focus on it again. */
        var lastOpenedId: String? = null

        fun select(category: VodCategory) {
            selectedKey.value = category.key
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun moviesWithProgress(category: VodCategory): Flow<List<VodItem>> =
            vod.observeMovies(category).flatMapLatest { movies ->
                vod.observeProgress(movies.map { it.id }).map { progress ->
                    movies.map { it.toItem(progress[it.id]) }
                }
            }
    }

internal val VodCategory.key: Pair<Long, String> get() = playlistId to name

private fun Movie.toItem(progress: WatchProgress?) = VodItem(id, name, posterUrl, year, rating, progress?.fraction())

private fun Series.toItem() = VodItem(id, name, posterUrl, year, rating, null)

internal fun WatchProgress.fraction(): Float? =
    if (durationMs <= 0 || finished) null else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

data class MovieUiState(
    val movie: Movie? = null,
    val details: MovieDetails? = null,
    val progress: WatchProgress? = null,
)

@HiltViewModel
class MovieViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val vod: VodRepository,
    ) : ViewModel() {
        private val id = savedState.toRoute<MovieRoute>().id
        private val _state = MutableStateFlow(MovieUiState())
        val state: StateFlow<MovieUiState> = _state.asStateFlow()

        init {
            viewModelScope.launch {
                _state.value = MovieUiState(movie = vod.movie(id), progress = vod.progress(id))
                _state.value = _state.value.copy(details = vod.movieDetails(id))
            }
            // Coming back from the player: show the new resume point.
            viewModelScope.launch {
                vod.observeProgress(listOf(id)).collect { map -> _state.value = _state.value.copy(progress = map[id]) }
            }
        }
    }

data class SeriesUiState(
    val series: Series? = null,
    val episodes: List<Episode> = emptyList(),
    val progress: Map<String, WatchProgress> = emptyMap(),
    val loading: Boolean = true,
    val error: RefreshError? = null,
)

@HiltViewModel
class SeriesViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val vod: VodRepository,
    ) : ViewModel() {
        private val id = savedState.toRoute<SeriesRoute>().id
        private val series = MutableStateFlow<Series?>(null)
        private val status = MutableStateFlow(true to (null as RefreshError?))

        @OptIn(ExperimentalCoroutinesApi::class)
        private val episodes: Flow<Pair<List<Episode>, Map<String, WatchProgress>>> =
            vod.observeEpisodes(id).flatMapLatest { list ->
                vod.observeProgress(list.map { it.id }).map { list to it }
            }

        val state: StateFlow<SeriesUiState> =
            combine(series, episodes, status) { s, (list, progress), (loading, error) ->
                SeriesUiState(s, list, progress, loading && list.isEmpty(), error)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SeriesUiState())

        init {
            viewModelScope.launch {
                series.value = vod.series(id)
                // Xtream lists episodes per series: fetch them every time the page opens.
                val error = vod.refreshEpisodes(id)
                status.value = false to error
            }
        }
    }

/** Title shown in the player and where the stream comes from. */
data class VodPlayerUiState(
    val title: String = "",
    val subtitle: String? = null,
    val missing: Boolean = false,
)

@HiltViewModel
class VodPlayerViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val vod: VodRepository,
        private val player: VodPlayer,
    ) : ViewModel() {
        private val route = savedState.toRoute<VodPlayerRoute>()
        private var contentId = route.contentId
        private val _ui = MutableStateFlow(VodPlayerUiState())
        val ui: StateFlow<VodPlayerUiState> = _ui.asStateFlow()
        val playback: StateFlow<VodState> = player.state

        val exoPlayer: Player? get() = player.player
        val positionMs: Long get() = player.positionMs
        val durationMs: Long get() = player.durationMs

        init {
            viewModelScope.launch { start(contentId, fromStart = route.fromStart) }
            // Resume point every 10 s, so a crash or a power cut loses little.
            viewModelScope.launch {
                while (true) {
                    delay(10_000)
                    saveProgress()
                }
            }
            // End of an episode: the next one plays.
            viewModelScope.launch {
                player.state.collect { s -> if (s.status == VodState.Status.ENDED) onEnded() }
            }
        }

        fun togglePause() = player.togglePause()

        fun seekBy(deltaMs: Long) = player.seekBy(deltaMs)

        private suspend fun start(
            id: String,
            fromStart: Boolean,
        ) {
            val episode = if (isEpisode(id)) vod.episode(id) else null
            val movie = if (episode == null) vod.movie(id) else null
            val url = episode?.streamUrl ?: movie?.streamUrl
            if (url == null) {
                _ui.value = VodPlayerUiState(missing = true)
                return
            }
            if (episode != null) {
                val series = vod.series(episode.seriesId)
                _ui.value =
                    VodPlayerUiState(
                        title = series?.name ?: episode.title,
                        subtitle = "S${episode.season} E${episode.episode} · ${cleanEpisodeTitle(
                            episode.title,
                            series?.name,
                        )}",
                    )
            } else {
                _ui.value = VodPlayerUiState(title = movie!!.name, subtitle = movie.year?.toString())
            }
            val resume = vod.progress(id)?.takeIf { !fromStart && !it.finished }?.positionMs ?: 0
            contentId = id
            player.play(id, url, resume)
        }

        private suspend fun onEnded() {
            saveProgress()
            if (!isEpisode(contentId)) return
            val current = vod.episode(contentId) ?: return
            val all = vod.observeEpisodes(current.seriesId).first()
            val next = all.getOrNull(all.indexOfFirst { it.id == contentId } + 1) ?: return
            start(next.id, fromStart = true)
        }

        private suspend fun saveProgress() {
            val duration = player.durationMs
            if (duration <= 0 || player.state.value.contentId != contentId) return
            vod.saveProgress(WatchProgress(contentId, player.positionMs, duration))
        }

        override fun onCleared() {
            // The ViewModel scope is already cancelled here: save the resume point in a short-lived scope.
            val duration = player.durationMs
            val position = player.positionMs
            val id = contentId
            player.stop()
            if (duration > 0) {
                kotlinx.coroutines.MainScope().launch { vod.saveProgress(WatchProgress(id, position, duration)) }
            }
        }

        private fun isEpisode(id: String) = "::e:" in id
    }

/** "Show - S01E02 - Title" → "Title" (falls back to the raw title). */
internal fun cleanEpisodeTitle(
    title: String,
    seriesName: String?,
): String {
    var text = title.trim()
    if (seriesName != null) {
        val bare = seriesName.replace(Regex("""\s*\(\d{4}\)\s*$"""), "")
        if (text.startsWith(bare, ignoreCase = true)) text = text.substring(bare.length)
    }
    text = text.replace(Regex("""^[\s\-:|.]*S\d{1,2}\s*E\d{1,3}[\s\-:|.]*""", RegexOption.IGNORE_CASE), "")
    return text.trim(' ', '-', ':', '|').ifEmpty { title }
}
