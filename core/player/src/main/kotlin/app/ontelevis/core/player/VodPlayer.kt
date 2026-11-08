package app.ontelevis.core.player

import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class VodState(
    val contentId: String? = null,
    val status: Status = Status.IDLE,
    val playing: Boolean = false,
    val error: PlaybackError? = null,
) {
    enum class Status { IDLE, BUFFERING, READY, ENDED, ERROR }
}

/**
 * Plays movies and episodes. Separate from [LivePlayer] (whose recovery rules are for live
 * streams); the live channel is held while a movie plays and resumes afterwards. Main thread only.
 */
@Singleton
class VodPlayer
    @Inject
    constructor(
        private val factory: PlayerFactory,
        private val livePlayer: LivePlayer,
    ) {
        private var exo: ExoPlayer? = null

        private val _state = MutableStateFlow(VodState())
        val state: StateFlow<VodState> = _state.asStateFlow()

        val player: Player? get() = exo

        val positionMs: Long get() = exo?.currentPosition ?: 0

        val durationMs: Long get() = exo?.duration?.takeIf { it > 0 } ?: 0

        fun play(
            contentId: String,
            url: String,
            startMs: Long,
        ) {
            livePlayer.hold()
            val player =
                exo ?: factory.create().also { created ->
                    created.addListener(listener)
                    exo = created
                }
            _state.value = VodState(contentId = contentId, status = VodState.Status.BUFFERING)
            player.stop()
            player.setMediaItem(MediaItem.fromUri(url), startMs.coerceAtLeast(0))
            player.prepare()
            player.playWhenReady = true
        }

        fun togglePause() {
            val player = exo ?: return
            player.playWhenReady = !player.playWhenReady
        }

        fun pause() {
            exo?.playWhenReady = false
        }

        fun seekBy(deltaMs: Long) {
            val player = exo ?: return
            val duration = durationMs
            val target = (player.currentPosition + deltaMs).coerceAtLeast(0)
            player.seekTo(if (duration > 0) target.coerceAtMost(duration - 1_000) else target)
        }

        /** Leaving the player: stop, free the decoder and let the live channel come back. */
        fun stop() {
            exo?.release()
            exo = null
            _state.value = VodState()
            livePlayer.release()
        }

        private val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    val status =
                        when (playbackState) {
                            Player.STATE_BUFFERING -> VodState.Status.BUFFERING
                            Player.STATE_READY -> VodState.Status.READY
                            Player.STATE_ENDED -> VodState.Status.ENDED
                            else -> return
                        }
                    _state.update { it.copy(status = status, error = null) }
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _state.update { it.copy(playing = isPlaying) }
                }

                override fun onPlayerError(error: PlaybackException) {
                    val reason =
                        when (error.kind()) {
                            FailureKind.GONE -> PlaybackError.NOT_FOUND
                            FailureKind.FORMAT -> PlaybackError.UNSUPPORTED
                            FailureKind.DECODER -> PlaybackError.DEVICE_CANNOT_DECODE
                            else -> PlaybackError.UNREACHABLE
                        }
                    _state.update { it.copy(status = VodState.Status.ERROR, error = reason) }
                }
            }
    }
