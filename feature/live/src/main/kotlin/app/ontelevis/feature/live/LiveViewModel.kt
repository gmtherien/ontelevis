package app.ontelevis.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import app.ontelevis.core.data.ChannelRepository
import app.ontelevis.core.data.EpgRepository
import app.ontelevis.core.data.PlaybackPrefs
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ChannelGroup
import app.ontelevis.core.model.Playlist
import app.ontelevis.core.model.Programme
import app.ontelevis.core.model.Shortcut
import app.ontelevis.core.model.epg.EpgWindow
import app.ontelevis.core.player.LivePlayer
import app.ontelevis.core.player.LiveState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import javax.inject.Inject

data class LiveUiState(
    /** Null while loading. */
    val playlists: List<Playlist>? = null,
    /** Favourites and History, with their channel counts. */
    val shortcuts: List<ChannelGroup> = emptyList(),
    /** Provider categories shown in the panel (hidden ones only while [showHidden]). */
    val groups: List<ChannelGroup> = emptyList(),
    /** At least one playlist has categories (some may be hidden). */
    val hasCategories: Boolean = false,
    val hiddenCount: Int = 0,
    val showHidden: Boolean = false,
    val selectedGroup: ChannelGroup? = null,
    val favoriteIds: Set<String> = emptySet(),
)

/**
 * Guide programmes by channel id, for the category [groupKey]: focus waits for them, since a row
 * without them shows a placeholder block that goes away (with the focus) when they arrive.
 */
data class GuideProgrammes(
    val groupKey: Pair<Long, String>? = null,
    val byChannel: Map<String, List<Programme>> = emptyMap(),
)

/** Channels of one category; [groupKey] tells which, so focus waits for the right list. */
data class GroupChannels(
    val groupKey: Pair<Long, String>? = null,
    val list: List<Channel> = emptyList(),
)

@HiltViewModel
class LiveViewModel
    @Inject
    constructor(
        playlistRepository: PlaylistRepository,
        private val channelRepository: ChannelRepository,
        private val epgRepository: EpgRepository,
        private val livePlayer: LivePlayer,
        private val prefs: PlaybackPrefs,
    ) : ViewModel() {
        private val selectedKey = MutableStateFlow<Pair<Long, String>?>(null)
        private val showHidden = MutableStateFlow(false)
        private val favorites = channelRepository.observeFavorites()
        private val history = channelRepository.observeHistory()

        private val shortcuts: Flow<List<ChannelGroup>> =
            combine(favorites, history) { fav, hist ->
                listOf(ChannelGroup.of(Shortcut.FAVORITES, fav.size), ChannelGroup.of(Shortcut.HISTORY, hist.size))
            }

        val state: StateFlow<LiveUiState> =
            combine(
                playlistRepository.observePlaylists(),
                channelRepository.observeGroups(),
                shortcuts,
                combine(selectedKey, showHidden, channelRepository.observeFavoriteIds()) { a, b, c -> Triple(a, b, c) },
            ) { playlists, all, shortcuts, (key, showHidden, favoriteIds) ->
                val hiddenCount = all.count { it.hidden }
                val shown = if (showHidden || hiddenCount == 0) all else all.filter { !it.hidden }
                val selected =
                    shortcuts.firstOrNull { it.key == key }
                        ?: shown.firstOrNull { it.key == key }
                        ?: shown.firstOrNull()
                        ?: shortcuts.firstOrNull { it.channelCount > 0 }
                LiveUiState(
                    playlists = playlists,
                    shortcuts = shortcuts,
                    groups = shown,
                    hasCategories = all.isNotEmpty(),
                    hiddenCount = hiddenCount,
                    showHidden = showHidden && hiddenCount > 0,
                    selectedGroup = selected,
                    favoriteIds = favoriteIds,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

        @OptIn(ExperimentalCoroutinesApi::class)
        val channels: StateFlow<GroupChannels> =
            state
                .map { it.selectedGroup }
                // Channel counts change on refresh; only a different group means new channels.
                .distinctUntilChangedBy { it?.key }
                .flatMapLatest { group ->
                    when {
                        group == null -> flowOf(emptyList())
                        group.shortcut == Shortcut.FAVORITES -> favorites
                        group.shortcut == Shortcut.HISTORY -> history
                        else -> channelRepository.observeChannels(group)
                    }.map { GroupChannels(group?.key, it) }
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GroupChannels())

        val live: StateFlow<LiveState> = livePlayer.state

        val player: Player? get() = livePlayer.player

        /** Current time, updated at each new minute (now line, progress bars, "N min left"). */
        val now: StateFlow<Instant> = minuteTicker().stateIn(viewModelScope, SharingStarted.Eagerly, Instant.now())

        /**
         * Guide window: 8 h from half an hour before the current slot. Its start stays put while the
         * screen is used (moving it every half hour would shift the whole timeline under the user);
         * it is re-anchored on "now" once now is [REANCHOR_AFTER] past it.
         */
        private val _window = MutableStateFlow(EpgWindow.around(Instant.now(), duration = GUIDE_SPAN))
        val window: StateFlow<EpgWindow> = _window.asStateFlow()

        /** Last programme replayed from the guide (channel id, start): Back from the recording lands on it. */
        var lastReplay: Pair<String, Long>? = null

        // Start of the window around "now"; the window itself may reach further back (catch-up).
        private var anchor: Instant = _window.value.start

        // Changes when a guide finished downloading: programmes are read again.
        private val epgStamp =
            playlistRepository
                .observePlaylists()
                .map { list ->
                    list.map { it.id to it.lastEpgAt }
                }.distinctUntilChanged()

        /** Programmes of the visible category in the guide window, by channel id. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val programmes: StateFlow<GuideProgrammes> =
            combine(channels, window, epgStamp) { channels, window, _ -> channels to window }
                .mapLatest { (channels, window) ->
                    GuideProgrammes(
                        channels.groupKey,
                        epgRepository.programmes(channels.list, window.start.toEpochMilli(), window.end.toEpochMilli()),
                    )
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GuideProgrammes())

        /** Now and next of the channel in the player (info panel). */
        val playingProgrammes: StateFlow<List<Programme>> =
            nowNextFlow(
                livePlayer,
                epgRepository,
                now,
                epgStamp,
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        init {
            viewModelScope.launch {
                now.collect { now ->
                    if (now.isAfter(anchor.plus(REANCHOR_AFTER))) {
                        val around = EpgWindow.around(now, duration = GUIDE_SPAN)
                        // Hours added into the past for catch-up stay; the end follows "now".
                        val start = if (_window.value.start.isBefore(anchor)) _window.value.start else around.start
                        anchor = around.start
                        _window.value = EpgWindow(start, Duration.between(start, around.end))
                    }
                }
            }
            resumeLastChannel()
            // Remember every channel that plays (from here, CH+ / CH- or full screen) for the next start.
            viewModelScope.launch {
                livePlayer.state
                    .map { it.channel?.id }
                    .filterNotNull()
                    .distinctUntilChanged()
                    .collect { prefs.lastChannelId = it }
            }
            // ...and add it to the History shortcut.
            viewModelScope.launch {
                livePlayer.state
                    .map { it.channel }
                    .filterNotNull()
                    .distinctUntilChangedBy { it.id }
                    .collect { channelRepository.recordWatched(it) }
            }
        }

        /**
         * Catch-up: the guide reaches [PAST_STEP] further back, no further than [channel]'s archive.
         * Called when the focus gets near the start of the window on a channel with catch-up.
         */
        fun extendIntoPast(channel: Channel) {
            val days = channel.catchupDays.coerceIn(0, MAX_CATCHUP_DAYS)
            if (days == 0) return
            val earliest = slotFloor(Instant.now().minus(Duration.ofDays(days.toLong())))
            val current = _window.value
            if (!current.start.isAfter(earliest)) return
            val start = maxOf(earliest, current.start.minus(PAST_STEP))
            _window.value = EpgWindow(start, Duration.between(start, current.end))
        }

        fun select(group: ChannelGroup) {
            selectedKey.value = group.key
        }

        /** Plays [channel] in the preview; CH+ / CH- then move through the current category. */
        fun play(channel: Channel) {
            livePlayer.play(channel, channels.value.list)
        }

        /** Returns true when [channel] is now a favourite. */
        suspend fun toggleFavorite(channel: Channel): Boolean = channelRepository.toggleFavorite(channel)

        fun moveGroup(
            group: ChannelGroup,
            step: Int,
        ) {
            viewModelScope.launch { channelRepository.moveGroup(group, step) }
        }

        fun setGroupHidden(
            group: ChannelGroup,
            hidden: Boolean,
        ) {
            viewModelScope.launch { channelRepository.setGroupHidden(group, hidden) }
        }

        fun toggleShowHidden() {
            showHidden.value = !showHidden.value
        }

        fun clearHistory() {
            viewModelScope.launch { channelRepository.clearHistory() }
        }

        /** Startup: play the last channel in the preview and open its category. */
        private fun resumeLastChannel() {
            if (livePlayer.state.value.channel != null) return
            val id = prefs.lastChannelId ?: return
            viewModelScope.launch {
                val channel = channelRepository.channel(id) ?: return@launch
                selectedKey.value = channel.playlistId to channel.group
                livePlayer.play(channel, channelRepository.channelsAround(channel))
            }
        }
    }

/** Full-screen player: same player instance as the preview. */
@HiltViewModel
class PlayerViewModel
    @Inject
    constructor(
        private val livePlayer: LivePlayer,
        epgRepository: EpgRepository,
        playlistRepository: PlaylistRepository,
    ) : ViewModel() {
        val live: StateFlow<LiveState> = livePlayer.state

        /** Now and next of the channel in the player (info bar). */
        val programmes: StateFlow<List<Programme>> =
            nowNextFlow(
                livePlayer,
                epgRepository,
                minuteTicker(),
                playlistRepository.observePlaylists().map { list -> list.map { it.lastEpgAt } }.distinctUntilChanged(),
            ).stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

        val player: Player? get() = livePlayer.player

        fun zap(step: Int) {
            livePlayer.zap(step)
        }

        fun lastChannel() {
            livePlayer.lastChannel()
        }

        fun retry() {
            livePlayer.state.value.channel
                ?.let { livePlayer.play(it) }
        }
    }

internal val ChannelGroup.key: Pair<Long, String> get() = playlistId to name

/** The guide loads 8 h (half an hour back, 7.5 h ahead) and scrolls sideways through it. */
private val GUIDE_SPAN: Duration = Duration.ofHours(8)

/** Re-anchor the guide window on "now" when its start is this far behind. */
private val REANCHOR_AFTER: Duration = Duration.ofMinutes(90)

/** How much the guide reaches further back each time the focus nears its start (catch-up). */
private val PAST_STEP: Duration = Duration.ofHours(6)

/** Catch-up archives longer than this are not followed back (same cap as the guide import). */
private const val MAX_CATCHUP_DAYS = 7

private fun slotFloor(t: Instant): Instant {
    val slot = 30 * 60 * 1000L
    return Instant.ofEpochMilli(Math.floorDiv(t.toEpochMilli(), slot) * slot)
}

/** Emits the time now, then at the start of every minute. */
internal fun minuteTicker(): Flow<Instant> =
    flow {
        while (true) {
            val now = Instant.now()
            emit(now)
            delay(60_000 - now.toEpochMilli() % 60_000)
        }
    }

/** Programmes of the channel in the player, from now to 12 h ahead (first = on now). */
@OptIn(ExperimentalCoroutinesApi::class)
internal fun nowNextFlow(
    livePlayer: LivePlayer,
    epg: EpgRepository,
    clock: Flow<Instant>,
    epgStamp: Flow<Any>,
): Flow<List<Programme>> =
    combine(
        livePlayer.state.map { it.channel }.distinctUntilChangedBy { it?.id },
        clock,
        epgStamp,
    ) { channel, now, _ -> channel to now }
        .mapLatest { (channel, now) ->
            if (channel == null) return@mapLatest emptyList()
            val from = now.toEpochMilli()
            epg.programmes(listOf(channel), from, from + 12 * 60 * 60 * 1000L)[channel.id].orEmpty()
        }
