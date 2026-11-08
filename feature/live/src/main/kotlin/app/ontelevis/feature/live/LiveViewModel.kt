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
    val groups: List<ChannelGroup> = emptyList(),
    val selectedGroup: ChannelGroup? = null,
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

        val state: StateFlow<LiveUiState> =
            combine(
                playlistRepository.observePlaylists(),
                channelRepository.observeGroups(),
                selectedKey,
            ) { playlists, groups, key ->
                val selected = groups.firstOrNull { it.key == key } ?: groups.firstOrNull()
                LiveUiState(playlists, groups, selected)
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LiveUiState())

        @OptIn(ExperimentalCoroutinesApi::class)
        val channels: StateFlow<List<Channel>> =
            state
                .map { it.selectedGroup }
                // Channel counts change on refresh; only a different group means new channels.
                .distinctUntilChangedBy { it?.key }
                .flatMapLatest { group ->
                    if (group == null) flowOf(emptyList()) else channelRepository.observeChannels(group)
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

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

        // Changes when a guide finished downloading: programmes are read again.
        private val epgStamp =
            playlistRepository
                .observePlaylists()
                .map { list ->
                    list.map { it.id to it.lastEpgAt }
                }.distinctUntilChanged()

        /** Programmes of the visible category in the guide window, by channel id. */
        @OptIn(ExperimentalCoroutinesApi::class)
        val programmes: StateFlow<Map<String, List<Programme>>> =
            combine(channels, window, epgStamp) { channels, window, _ -> channels to window }
                .mapLatest { (channels, window) ->
                    epgRepository.programmes(channels, window.start.toEpochMilli(), window.end.toEpochMilli())
                }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

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
                    if (now.isAfter(_window.value.start.plus(REANCHOR_AFTER))) {
                        _window.value = EpgWindow.around(now, duration = GUIDE_SPAN)
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
        }

        fun select(group: ChannelGroup) {
            selectedKey.value = group.key
        }

        /** Plays [channel] in the preview; CH+ / CH- then move through the current category. */
        fun play(channel: Channel) {
            livePlayer.play(channel, channels.value)
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
