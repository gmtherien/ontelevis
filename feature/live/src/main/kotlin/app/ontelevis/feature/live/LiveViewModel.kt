package app.ontelevis.feature.live

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.Player
import app.ontelevis.core.data.CategoryKind
import app.ontelevis.core.data.CategoryRef
import app.ontelevis.core.data.ChannelRepository
import app.ontelevis.core.data.EpgRepository
import app.ontelevis.core.data.LaunchRequest
import app.ontelevis.core.data.LaunchRequests
import app.ontelevis.core.data.ParentalControl
import app.ontelevis.core.data.PlaybackPrefs
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.data.reminder.ReminderRepository
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
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.receiveAsFlow
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
    /** Categories locked by the parental PIN (padlock), and those still closed this session. */
    val lockedKeys: Set<Pair<Long, String>> = emptySet(),
    val closedKeys: Set<Pair<Long, String>> = emptySet(),
    val hasPin: Boolean = false,
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
    /** The category is locked and the PIN was not entered: no channels shown. */
    val locked: Boolean = false,
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
        private val parental: ParentalControl,
        private val launchRequests: LaunchRequests,
        private val reminderRepository: ReminderRepository,
    ) : ViewModel() {
        private val selectedKey = MutableStateFlow(prefs.lastGroup)

        /** Programme starts with a reminder, by channel id (bell in the guide). */
        val reminders: StateFlow<Map<String, Set<Long>>> =
            reminderRepository.observe().stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

        private val showHidden = MutableStateFlow(false)

        private val lockedLive: Flow<Set<Pair<Long, String>>> = parental.locked.map { it.liveKeys() }
        private val closedLive: Flow<Set<Pair<Long, String>>> = parental.closed.map { it.liveKeys() }

        // Channels of closed categories stay out of the shortcuts.
        private val favorites =
            combine(channelRepository.observeFavorites(), closedLive) { list, closed ->
                list.filter { (it.playlistId to it.group) !in closed }
            }
        private val history =
            combine(channelRepository.observeHistory(), closedLive) { list, closed ->
                list.filter { (it.playlistId to it.group) !in closed }
            }

        private val shortcuts: Flow<List<ChannelGroup>> =
            combine(favorites, history) { fav, hist ->
                listOf(ChannelGroup.of(Shortcut.FAVORITES, fav.size), ChannelGroup.of(Shortcut.HISTORY, hist.size))
            }

        val state: StateFlow<LiveUiState> =
            combine(
                playlistRepository.observePlaylists(),
                channelRepository.observeGroups(),
                shortcuts,
                combine(
                    combine(
                        selectedKey,
                        showHidden,
                        channelRepository.observeFavoriteIds(),
                    ) { a, b, c -> Triple(a, b, c) },
                    combine(lockedLive, closedLive, parental.hasPin) { a, b, c -> Triple(a, b, c) },
                ) { a, b -> a to b },
            ) { playlists, all, shortcuts, (selection, locks) ->
                val (key, showHidden, favoriteIds) = selection
                val (lockedKeys, closedKeys, hasPin) = locks
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
                    lockedKeys = lockedKeys,
                    closedKeys = closedKeys,
                    hasPin = hasPin,
                )
            }.stateIn(viewModelScope, SharingStarted.Eagerly, LiveUiState())

        @OptIn(ExperimentalCoroutinesApi::class)
        val channels: StateFlow<GroupChannels> =
            combine(
                state
                    .map { it.selectedGroup }
                    // Channel counts change on refresh; only a different group means new channels.
                    .distinctUntilChangedBy { it?.key },
                closedLive,
            ) { group, closed -> group to (group?.key in closed) }
                .distinctUntilChangedBy { (group, closed) -> group?.key to closed }
                .flatMapLatest { (group, closed) ->
                    when {
                        group == null -> flowOf(GroupChannels())
                        closed -> flowOf(GroupChannels(group.key, locked = true))
                        group.shortcut == Shortcut.FAVORITES -> favorites.map { GroupChannels(group.key, it) }
                        group.shortcut == Shortcut.HISTORY -> history.map { GroupChannels(group.key, it) }
                        else -> channelRepository.observeChannels(group).map { GroupChannels(group.key, it) }
                    }
                }.stateIn(viewModelScope, SharingStarted.Eagerly, GroupChannels())

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
                        epgRepository.programmes(
                            channels.list,
                            window.start.toEpochMilli(),
                            window.end.toEpochMilli(),
                            withDescription = false,
                        ),
                    )
                }.stateIn(viewModelScope, SharingStarted.Eagerly, GuideProgrammes())

        /** Now and next of the channel in the player (info panel). */
        val playingProgrammes: StateFlow<List<Programme>> =
            nowNextFlow(
                livePlayer,
                epgRepository,
                now,
                epgStamp,
            ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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
            // A channel opened from outside (reminder, Home, voice search): its category opens here.
            viewModelScope.launch {
                launchRequests.groupToOpen.filterNotNull().collect { key ->
                    selectedKey.value = key
                    launchRequests.groupOpened(key)
                }
            }
            // Remember every channel that plays (from here, CH+ / CH- or full screen) for the next start.
            viewModelScope.launch {
                livePlayer.state
                    .map { it.channel }
                    .filterNotNull()
                    .distinctUntilChangedBy { it.id }
                    .collect {
                        prefs.lastChannelId = it.id
                        prefs.lastGroup = it.playlistId to it.group
                    }
            }
            // A channel of a category that locks again (the app went to the background) stops.
            viewModelScope.launch {
                combine(livePlayer.state.map { it.channel }, closedLive) { channel, closed ->
                    channel != null && (channel.playlistId to channel.group) in closed
                }.collect { lockedNow -> if (lockedNow) livePlayer.stop() }
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

        /** [programme] with its description (the guide grid loads programmes without them). */
        suspend fun describe(
            channel: Channel,
            programme: Programme,
        ): Programme = programme.copy(description = epgRepository.description(channel, programme.startMillis))

        fun checkPin(pin: String): Boolean = parental.checkPin(pin)

        fun setPin(pin: String) = parental.setPin(pin)

        /** The PIN was entered for [group]: open it for this session. */
        fun unlock(group: ChannelGroup) = parental.unlock(group.lockRef())

        fun setLocked(
            group: ChannelGroup,
            locked: Boolean,
        ) {
            viewModelScope.launch { parental.setLocked(group.lockRef(), locked) }
        }

        fun toggleShowHidden() {
            showHidden.value = !showHidden.value
        }

        fun clearHistory() {
            viewModelScope.launch { channelRepository.clearHistory() }
        }

        /** Returns true when a reminder is now set for [programme]. */
        suspend fun toggleReminder(
            channel: Channel,
            programme: Programme,
        ): Boolean = reminderRepository.toggle(channel, programme)

        /** Startup: play the last channel in the preview and open its category. */
        private fun resumeLastChannel() {
            if (livePlayer.state.value.channel != null) return
            // Started to play a given channel: that one, not the last one.
            if (launchRequests.pending.value is LaunchRequest.PlayChannel) return
            val id = prefs.lastChannelId ?: return
            viewModelScope.launch {
                val channel = channelRepository.channel(id) ?: return@launch
                // A locked category does not open (nor play) without the PIN.
                if ((channel.playlistId to channel.group) in closedLive.first()) return@launch
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
            ).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

        /** Live pause is on (Settings › Playback). */
        val canPause: Boolean get() = livePlayer.canPause || livePlayer.state.value.paused

        fun togglePause() = livePlayer.togglePause()

        fun backToLive() = livePlayer.backToLive()
    }

internal val ChannelGroup.key: Pair<Long, String> get() = playlistId to name

private fun ChannelGroup.lockRef() = CategoryRef(CategoryKind.LIVE, playlistId, name)

private fun Set<CategoryRef>.liveKeys(): Set<Pair<Long, String>> =
    filter { it.kind == CategoryKind.LIVE }.map { it.playlistId to it.name }.toSet()

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
