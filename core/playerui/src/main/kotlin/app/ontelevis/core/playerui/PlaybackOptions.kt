package app.ontelevis.core.playerui

import androidx.media3.common.Player
import androidx.media3.common.Tracks
import app.ontelevis.core.data.PlaybackPref
import app.ontelevis.core.data.PlaybackPrefRepository
import app.ontelevis.core.data.PlayerSettings
import app.ontelevis.core.model.PictureFormat
import app.ontelevis.core.player.LivePlayer
import app.ontelevis.core.player.TrackOption
import app.ontelevis.core.player.VodPlayer
import app.ontelevis.core.player.applyTrackDefaults
import app.ontelevis.core.player.hideSubtitles
import app.ontelevis.core.player.selectTrack
import app.ontelevis.core.player.trackOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Tracks and picture format of what plays, remembered per channel, series or movie: each new
 * channel or title starts with its saved choices (else the defaults of Settings), and the user's
 * choices in the options menu are saved for it. One [PlayerSession] for the live player, one for
 * movies and episodes. Main thread only; [start] once from the activity.
 */
@Singleton
class PlaybackOptions
    @Inject
    constructor(
        private val livePlayer: LivePlayer,
        private val vodPlayer: VodPlayer,
        private val prefs: PlaybackPrefRepository,
        val settings: PlayerSettings,
        private val sessions: MediaSessions,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val live = PlayerSession()
        val vod = PlayerSession()
        private var started = false

        fun start() {
            if (started) return
            started = true
            scope.launch {
                livePlayer.state.map { it.channel?.id }.distinctUntilChanged().collect { id ->
                    live.bind(livePlayer.player, id)
                    if (vodPlayer.state.value.contentId == null) sessions.use(livePlayer.player)
                }
            }
            scope.launch { settings.timeshift.collect { livePlayer.timeshiftEnabled = it } }
            scope.launch {
                vodPlayer.state.map { it.contentId }.distinctUntilChanged().collect { id ->
                    sessions.use(if (id != null) vodPlayer.player else livePlayer.player)
                    vod.bind(vodPlayer.player, id?.let { keyFor(it) })
                }
            }
        }

        /** Catch-up of a programme follows its channel's choices. */
        private suspend fun keyFor(contentId: String): String =
            if (contentId.startsWith(CATCHUP_PREFIX)) {
                contentId.removePrefix(CATCHUP_PREFIX).substringBeforeLast(':')
            } else {
                prefs.keyForContent(contentId)
            }

        inner class PlayerSession internal constructor() {
            private var player: Player? = null
            private var key: String? = null
            private var pref: PlaybackPref? = null
            private var tracksApplied = false
            private var loading: Job? = null
            private val _format = MutableStateFlow(settings.format.value)

            /** Picture format of what plays now. */
            val format: StateFlow<PictureFormat> = _format.asStateFlow()

            private val listener =
                object : Player.Listener {
                    override fun onTracksChanged(tracks: Tracks) {
                        if (!tracksApplied && !tracks.isEmpty) applySavedTracks()
                    }
                }

            internal fun bind(
                player: Player?,
                key: String?,
            ) {
                if (player !== this.player) {
                    this.player?.removeListener(listener)
                    player?.addListener(listener)
                    this.player = player
                }
                this.key = key
                pref = null
                tracksApplied = false
                loading?.cancel()
                if (key == null || player == null) return
                _format.value = settings.format.value
                player.applyTrackDefaults(settings.audioLanguage.value, settings.subtitleLanguage.value)
                loading =
                    scope.launch {
                        val saved = prefs.get(key) ?: return@launch
                        if (this@PlayerSession.key != key) return@launch
                        pref = saved
                        saved.format?.let { _format.value = it }
                        player.applyTrackDefaults(
                            saved.audioLanguage ?: settings.audioLanguage.value,
                            if (saved.subtitlesOff) null else saved.subtitleLanguage ?: settings.subtitleLanguage.value,
                        )
                        if (!player.currentTracks.isEmpty) applySavedTracks()
                    }
            }

            /** A language alone is applied by the preferences; the exact track needs its label too. */
            private fun applySavedTracks() {
                val player = player ?: return
                val saved = pref ?: return
                tracksApplied = true
                if (saved.audioLabel != null) {
                    player
                        .trackOptions(TrackOption.Kind.AUDIO)
                        .firstOrNull { it.rawLabel == saved.audioLabel && it.language == saved.audioLanguage }
                        ?.let { player.selectTrack(it) }
                }
                if (!saved.subtitlesOff && saved.subtitleLabel != null) {
                    player
                        .trackOptions(TrackOption.Kind.SUBTITLE)
                        .firstOrNull { it.rawLabel == saved.subtitleLabel && it.language == saved.subtitleLanguage }
                        ?.let { player.selectTrack(it) }
                }
            }

            /** [unnamed]: name of a track the stream does not describe, with `%d` for its number. */
            fun audioTracks(unnamed: String): List<TrackOption> =
                player?.trackOptions(TrackOption.Kind.AUDIO, unnamed = unnamed).orEmpty()

            fun subtitleTracks(unnamed: String): List<TrackOption> =
                player?.trackOptions(TrackOption.Kind.SUBTITLE, unnamed = unnamed).orEmpty()

            fun selectAudio(option: TrackOption) {
                player?.selectTrack(option)
                save { it.copy(audioLanguage = option.language, audioLabel = option.rawLabel) }
            }

            /** Null hides the subtitles. */
            fun selectSubtitles(option: TrackOption?) {
                val player = player ?: return
                if (option == null) {
                    player.hideSubtitles()
                    save { it.copy(subtitlesOff = true, subtitleLanguage = null, subtitleLabel = null) }
                } else {
                    player.selectTrack(option)
                    save {
                        it.copy(
                            subtitlesOff = false,
                            subtitleLanguage = option.language,
                            subtitleLabel = option.rawLabel,
                        )
                    }
                }
            }

            fun setFormat(format: PictureFormat) {
                _format.value = format
                save { it.copy(format = format) }
            }

            private fun save(change: (PlaybackPref) -> PlaybackPref) {
                val key = key ?: return
                pref = change(pref ?: PlaybackPref())
                scope.launch { prefs.update(key, change) }
            }
        }

        private companion object {
            const val CATCHUP_PREFIX = "catchup:"
        }
    }
