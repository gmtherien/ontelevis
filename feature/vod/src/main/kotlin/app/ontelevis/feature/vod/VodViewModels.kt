package app.ontelevis.feature.vod

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import androidx.navigation.toRoute
import app.ontelevis.core.data.CategoryKind
import app.ontelevis.core.data.CategoryRef
import app.ontelevis.core.data.ChannelRepository
import app.ontelevis.core.data.ParentalControl
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.data.VodPrefs
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
import app.ontelevis.core.model.parseChannelName
import app.ontelevis.core.player.VodPlayer
import app.ontelevis.core.player.VodState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.CollationKey
import java.text.Collator
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import kotlin.random.Random

/** A poster of the grid: a movie or a series, with its resume progress (movies). */
data class VodItem(
    val id: String,
    val title: String,
    val posterUrl: String?,
    val year: Int?,
    val rating: Double,
    val progress: Float?,
    /** "2024 · ★ 3.5" under the poster, built once here (not at every recomposition of the grid). */
    val meta: String? = null,
    /** Its provider category (parental locks apply to it in Favourites and search too). */
    val playlistId: Long = 0,
    val group: String = "",
)

/** The posters of one category; [categoryKey] tells which, so focus waits for the right list. */
data class VodItems(
    val categoryKey: Pair<Long, String>? = null,
    val list: List<VodItem> = emptyList(),
    /** The category is locked and the PIN was not entered: no posters shown. */
    val locked: Boolean = false,
)

data class VodBrowseState(
    /** Null while loading. */
    val playlists: List<Playlist>? = null,
    /** Favourites first, then the provider categories. */
    val categories: List<VodCategory> = emptyList(),
    /** The playlists have movie / series categories (Favourites alone does not count). */
    val hasProviderCategories: Boolean = false,
    val selected: VodCategory? = null,
    val favoriteIds: Set<String> = emptySet(),
    /** Categories locked by the parental PIN (padlock), and those still closed this session. */
    val lockedKeys: Set<Pair<Long, String>> = emptySet(),
    val closedKeys: Set<Pair<Long, String>> = emptySet(),
    val hasPin: Boolean = false,
)

/** How the posters of a category (or of a search) are ordered. */
enum class VodSort {
    /** As the provider lists them. */
    PROVIDER,

    /** Release year, most recent first. */
    NEWEST,
    OLDEST,

    /** Rating (stars), best first; unrated last. */
    TOP_RATED,
    A_TO_Z,
    Z_TO_A,
    RANDOM,
}

/** Movies or series grid with its categories. The kind is set by the screen ([setKind]). */
@HiltViewModel
class VodBrowseViewModel
    @Inject
    constructor(
        playlists: PlaylistRepository,
        private val vod: VodRepository,
        private val prefs: VodPrefs,
        private val parental: ParentalControl,
    ) : ViewModel() {
        private val kind = MutableStateFlow<VodKind?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val lockedKeys: Flow<Set<Pair<Long, String>>> =
            kind.flatMapLatest { k -> parental.locked.map { it.keysOf(k) } }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val closedKeys: Flow<Set<Pair<Long, String>>> =
            kind.flatMapLatest { k -> parental.closed.map { it.keysOf(k) } }

        /** Text typed in the search field; 2 characters or more replace the category by results. */
        private val _query = MutableStateFlow("")
        val query: StateFlow<String> = _query.asStateFlow()

        @OptIn(ExperimentalCoroutinesApi::class)
        val sort: StateFlow<VodSort> =
            kind
                .flatMapLatest { k -> if (k == null) flowOf(null) else prefs.sort(k) }
                .map { name -> VodSort.entries.firstOrNull { it.name == name } ?: VodSort.PROVIDER }
                .stateIn(viewModelScope, SharingStarted.Eagerly, VodSort.PROVIDER)

        // Random order stays the same while the screen is used (posters must not jump on refresh).
        private val randomSeed = System.nanoTime()
        private val selectedKey = MutableStateFlow<Pair<Long, String>?>(null)

        @OptIn(ExperimentalCoroutinesApi::class)
        private val categories: Flow<List<VodCategory>> =
            kind.flatMapLatest { k -> if (k == null) flowOf(emptyList()) else vod.observeCategories(k) }

        private val favoriteMovies = vod.observeFavoriteMovies()
        private val favoriteSeries = vod.observeFavoriteSeries()

        @OptIn(ExperimentalCoroutinesApi::class)
        private val favoritesCategory: Flow<VodCategory?> =
            kind.flatMapLatest { k ->
                when (k) {
                    null -> flowOf(null)
                    VodKind.MOVIE -> favoriteMovies.map { VodCategory.favorites(k, it.size) }
                    VodKind.SERIES -> favoriteSeries.map { VodCategory.favorites(k, it.size) }
                }
            }

        val state: StateFlow<VodBrowseState> =
            combine(
                playlists.observePlaylists(),
                categories,
                favoritesCategory,
                combine(selectedKey, vod.observeFavoriteIds()) { a, b -> a to b },
                combine(lockedKeys, closedKeys, parental.hasPin) { a, b, c -> Triple(a, b, c) },
            ) { lists, cats, favorites, (key, favoriteIds), (locked, closed, hasPin) ->
                val all = if (cats.isEmpty() || favorites == null) cats else listOf(favorites) + cats
                VodBrowseState(
                    playlists = lists,
                    categories = all,
                    hasProviderCategories = cats.isNotEmpty(),
                    // Opens on the first provider category, not on Favourites.
                    selected = all.firstOrNull { it.key == key } ?: cats.firstOrNull(),
                    favoriteIds = favoriteIds,
                    lockedKeys = locked,
                    closedKeys = closed,
                    hasPin = hasPin,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VodBrowseState())

        @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
        private val searchQuery: Flow<String?> =
            _query
                .map { it.trim().takeIf { q -> q.length >= MIN_SEARCH } }
                .distinctUntilChanged()
                // Wait for a pause in the typing before querying (big panels: hundreds of thousands of titles).
                .debounce { if (it == null) 0 else SEARCH_DEBOUNCE_MS }

        @OptIn(ExperimentalCoroutinesApi::class)
        private val unsorted: Flow<VodItems> =
            combine(
                state.map { it.selected }.distinctUntilChangedBy { it?.key to it?.kind },
                searchQuery,
                kind,
            ) { category, search, k -> Triple(category, search, k) }
                .flatMapLatest { (category, search, k) ->
                    when {
                        search != null && k != null -> {
                            searchItems(k, search).map { VodItems(searchKey(search), it) }
                        }

                        else -> {
                            when {
                                category == null -> flowOf(emptyList())
                                category.kind == VodKind.MOVIE -> moviesWithProgress(category)
                                category.isFavorites -> favoriteSeries.map { list -> list.map { it.toItem() } }
                                else -> vod.observeSeries(category).map { list -> list.map { it.toItem() } }
                            }.map { VodItems(category?.key, it) }
                        }
                    }
                }

        val items: StateFlow<VodItems> =
            combine(unsorted, sort, closedKeys) { items, sort, closed ->
                when {
                    items.categoryKey in closed -> {
                        VodItems(items.categoryKey, locked = true)
                    }

                    // Favourites and search leave out the titles of closed categories.
                    closed.isEmpty() -> {
                        items.copy(list = sorted(items.list, sort))
                    }

                    else -> {
                        items.copy(
                            list = sorted(items.list.filter { (it.playlistId to it.group) !in closed }, sort),
                        )
                    }
                }
            }.flowOn(Dispatchers.Default)
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VodItems())

        fun setQuery(text: String) {
            _query.value = text
        }

        fun setSort(value: VodSort) {
            kind.value?.let { prefs.setSort(it, value.name) }
        }

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun searchItems(
            kind: VodKind,
            query: String,
        ): Flow<List<VodItem>> =
            when (kind) {
                VodKind.MOVIE -> {
                    flow { emit(vod.searchMovies(query)) }.flatMapLatest { movies ->
                        vod.observeProgress(movies.map { it.id }).map { progress ->
                            movies.map { it.toItem(progress[it.id]) }
                        }
                    }
                }

                VodKind.SERIES -> {
                    flow { emit(vod.searchSeries(query).map { it.toItem() }) }
                }
            }

        private fun sorted(
            list: List<VodItem>,
            sort: VodSort,
        ): List<VodItem> {
            if (sort == VodSort.PROVIDER || sort == VodSort.RANDOM || list.size < 2) return sortedPlain(list, sort)
            // Title keys built once per item: a 14,000-movie category used to run two regexes and a full
            // Collator comparison on each of ~200,000 comparisons.
            val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
            val keys = HashMap<String, CollationKey>(list.size * 2)
            list.forEach { keys[it.id] = collator.getCollationKey(sortTitle(it.title)) }
            val byTitle = Comparator<VodItem> { a, b -> keys.getValue(a.id).compareTo(keys.getValue(b.id)) }
            return when (sort) {
                VodSort.NEWEST -> {
                    list.sortedWith(
                        compareBy<VodItem> { it.year == null }.thenByDescending { it.year }.then(byTitle),
                    )
                }

                VodSort.OLDEST -> {
                    list.sortedWith(
                        compareBy<VodItem> { it.year == null }.thenBy { it.year }.then(byTitle),
                    )
                }

                VodSort.TOP_RATED -> {
                    list.sortedWith(
                        compareBy<VodItem> { it.rating <= 0 }.thenByDescending { it.rating }.then(byTitle),
                    )
                }

                VodSort.A_TO_Z -> {
                    list.sortedWith(byTitle)
                }

                VodSort.Z_TO_A -> {
                    list.sortedWith(byTitle.reversed())
                }

                else -> {
                    list
                }
            }
        }

        /** Orders that need no title comparison. */
        private fun sortedPlain(
            list: List<VodItem>,
            sort: VodSort,
        ): List<VodItem> = if (sort == VodSort.RANDOM) list.shuffled(Random(randomSeed)) else list

        fun setKind(value: VodKind) {
            if (kind.value != value) kind.value = value
        }

        /** Last poster opened: Back from its page puts the focus on it again. */
        var lastOpenedId: String? = null

        fun select(category: VodCategory) {
            selectedKey.value = category.key
        }

        fun checkPin(pin: String): Boolean = parental.checkPin(pin)

        fun setPin(pin: String) = parental.setPin(pin)

        fun unlock(category: VodCategory) = parental.unlock(category.lockRef())

        fun setLocked(
            category: VodCategory,
            locked: Boolean,
        ) {
            viewModelScope.launch { parental.setLocked(category.lockRef(), locked) }
        }

        /** Returns true when the title is now a favourite. */
        suspend fun toggleFavorite(id: String): Boolean = vod.toggleFavorite(id, kind.value ?: VodKind.MOVIE)

        @OptIn(ExperimentalCoroutinesApi::class)
        private fun moviesWithProgress(category: VodCategory): Flow<List<VodItem>> =
            if (category.isFavorites) {
                favoriteMovies.flatMapLatest { movies ->
                    vod.observeProgress(movies.map { it.id }).map { progress ->
                        movies.map { it.toItem(progress[it.id]) }
                    }
                }
            } else {
                vod.observeMoviesWithProgress(category).map { rows ->
                    rows.map { (movie, progress) -> movie.toItem(progress) }
                }
            }
    }

internal val VodCategory.key: Pair<Long, String> get() = playlistId to name

/** Key of a search's results (in place of a category key). */
internal fun searchKey(query: String): Pair<Long, String> = -2L to query

private const val MIN_SEARCH = 2
private const val SEARCH_DEBOUNCE_MS = 300L

// Quality tags and two-letter language codes only: "ALIEN: Romulus" or "TRON: Legacy" keep their word.
private val PROVIDER_PREFIX = Regex("^(?:4K|8K|UHD|FHD|HD|SD|[A-Z]{2})\\s*[:|-]\\s*")

/** Title used for alphabetical order: without a provider prefix ("4K: ", "EN - ", "FR| "). */
internal fun sortTitle(title: String): String = title.replaceFirst(PROVIDER_PREFIX, "")

private fun Movie.toItem(progress: WatchProgress?) =
    VodItem(id, name, posterUrl, year, rating, progress?.fraction(), metaOf(year, rating), playlistId, group)

private fun Series.toItem() = VodItem(id, name, posterUrl, year, rating, null, metaOf(year, rating), playlistId, group)

private fun metaOf(
    year: Int?,
    rating: Double,
): String? =
    listOfNotNull(
        year?.toString(),
        rating.takeIf { it > 0 }?.let { "★ " + String.format(Locale.ROOT, "%.1f", it) },
    ).joinToString(" · ").ifEmpty { null }

private fun VodKind.lockKind() = if (this == VodKind.MOVIE) CategoryKind.MOVIE else CategoryKind.SERIES

private fun VodCategory.lockRef() = CategoryRef(kind.lockKind(), playlistId, name)

private fun Set<CategoryRef>.keysOf(kind: VodKind?): Set<Pair<Long, String>> =
    if (kind == null) emptySet() else filter { it.kind == kind.lockKind() }.map { it.playlistId to it.name }.toSet()

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
    /** A catch-up recording (errors are worded for the archive). */
    val catchup: Boolean = false,
)

@HiltViewModel
class VodPlayerViewModel
    @Inject
    constructor(
        savedState: SavedStateHandle,
        private val vod: VodRepository,
        private val player: VodPlayer,
        private val channels: ChannelRepository,
    ) : ViewModel() {
        private val route = savedState.toRoute<VodPlayerRoute>()

        /** A catch-up recording: no resume point, no next episode. */
        private val catchup = route.catchupChannelId != null
        private var contentId = route.contentId
        private val _ui = MutableStateFlow(VodPlayerUiState())
        val ui: StateFlow<VodPlayerUiState> = _ui.asStateFlow()
        val playback: StateFlow<VodState> = player.state

        val exoPlayer: Player? get() = player.player
        val positionMs: Long get() = player.positionMs
        val durationMs: Long get() = player.durationMs

        init {
            viewModelScope.launch {
                if (catchup) startCatchup() else start(contentId, fromStart = route.fromStart)
            }
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

        private suspend fun startCatchup() {
            val channel = route.catchupChannelId?.let { channels.channel(it) }
            val url = channel?.let { channels.catchupUrl(it, route.catchupStart, route.catchupStop) }
            if (channel == null || url == null) {
                _ui.value = VodPlayerUiState(missing = true)
                return
            }
            val zone = ZoneId.systemDefault()
            val start = Instant.ofEpochMilli(route.catchupStart).atZone(zone)
            val end = Instant.ofEpochMilli(route.catchupStop).atZone(zone)
            val day = start.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
            val hours = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
            _ui.value =
                VodPlayerUiState(
                    title = route.catchupTitle ?: parseChannelName(channel.name).title,
                    subtitle = "${parseChannelName(
                        channel.name,
                    ).title} · $day · ${start.format(hours)} – ${end.format(hours)}",
                )
            contentId = "catchup:${channel.id}:${route.catchupStart}"
            player.play(contentId, url, 0)
        }

        private suspend fun onEnded() {
            if (catchup) return
            saveProgress()
            if (!isEpisode(contentId)) return
            val current = vod.episode(contentId) ?: return
            val all = vod.observeEpisodes(current.seriesId).first()
            val next = all.getOrNull(all.indexOfFirst { it.id == contentId } + 1) ?: return
            start(next.id, fromStart = true)
        }

        private suspend fun saveProgress() {
            if (catchup) return
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
            if (duration > 0 && !catchup) {
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
