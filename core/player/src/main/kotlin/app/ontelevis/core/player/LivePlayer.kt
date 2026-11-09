package app.ontelevis.core.player

import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import app.ontelevis.core.model.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

data class LiveState(
    val channel: Channel? = null,
    val status: Status = Status.IDLE,
    val error: PlaybackError? = null,
    /** Height of the decoded video, 0 when unknown. */
    val videoHeight: Int = 0,
) {
    enum class Status { IDLE, BUFFERING, PLAYING, ERROR }
}

/**
 * The one live player of the app. The preview and the full-screen view show the same instance,
 * so going full screen never reconnects. Main thread only.
 */
@Singleton
class LivePlayer
    @Inject
    constructor(
        private val factory: PlayerFactory,
    ) {
        private var exo: ExoPlayer? = null
        private val handler = Handler(Looper.getMainLooper())
        private val policy = RecoveryPolicy()
        private var asHls = false
        private var suspended = false

        // A movie is playing: the live channel stays released, even when the app comes back.
        private var held = false
        private var zapList: List<Channel> = emptyList()
        private var previous: Channel? = null

        private val _state = MutableStateFlow(LiveState())
        val state: StateFlow<LiveState> = _state.asStateFlow()

        /** The player to attach to a surface; null until something was played. */
        val player: Player? get() = exo

        /** Plays [channel]. [zapList] is the list CH+ / CH- move through (usually its category). */
        fun play(
            channel: Channel,
            zapList: List<Channel> = this.zapList,
        ) {
            if (zapList.isNotEmpty()) this.zapList = zapList
            val current = _state.value.channel
            if (current?.id == channel.id && _state.value.status != LiveState.Status.ERROR) return
            if (current != null) previous = current
            policy.reset()
            asHls = channel.streamUrl.isHlsUrl()
            // The player exists before the state names the channel: state watchers (track choices)
            // find it there.
            ensurePlayer()
            _state.value = LiveState(channel = channel, status = LiveState.Status.BUFFERING)
            open(channel)
        }

        /** CH+ (`step = 1`) / CH- (`step = -1`) through the zap list. Returns the new channel. */
        fun zap(step: Int): Channel? {
            val list = zapList
            if (list.isEmpty()) return null
            val index = list.indexOfFirst { it.id == _state.value.channel?.id }
            val next = list[Math.floorMod(index + step, list.size)]
            play(next)
            return next
        }

        /** Back to the channel watched before this one. */
        fun lastChannel(): Channel? = previous?.also { play(it) }

        /** App goes to the background: release the connection, remember the channel. */
        fun suspend() {
            suspended = true
            handler.removeCallbacksAndMessages(null)
            exo?.stop()
        }

        /** App comes back: reconnect to the channel that was playing. */
        fun resume() {
            if (!suspended || held) return
            suspended = false
            val channel = _state.value.channel ?: return
            policy.reset()
            _state.update { it.copy(status = LiveState.Status.BUFFERING, error = null) }
            open(channel)
        }

        /** A movie starts: release the live channel until [release]. */
        fun hold() {
            held = true
            suspend()
        }

        /** The movie ended: the live channel comes back (if the app is in the foreground). */
        fun release() {
            held = false
            resume()
        }

        fun stop() {
            handler.removeCallbacksAndMessages(null)
            exo?.stop()
            _state.value = LiveState()
        }

        private fun open(channel: Channel) {
            openedAt = SystemClock.elapsedRealtime()
            handler.removeCallbacksAndMessages(null)
            val player = ensurePlayer()
            val item =
                MediaItem
                    .Builder()
                    .setUri(channel.streamUrl)
                    .apply { if (asHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                    .build()
            // Release the video decoder before the next stream. Kept enabled, ExoPlayer reuses it and
            // "reconfigures" it for the new resolution; some decoders (the emulator's, some MTK boxes)
            // then draw a 720p picture in the old 1080p buffers: small image in a corner, the previous
            // channel's last frame around it. A fresh decoder costs a few tens of ms per zap.
            player.stop()
            player.setMediaItem(item)
            player.prepare()
            player.playWhenReady = true
        }

        private fun ensurePlayer(): ExoPlayer =
            exo ?: factory.create(live = true).also { created ->
                created.addListener(listener)
                exo = created
            }

        private fun apply(action: RecoveryAction) {
            val channel = _state.value.channel ?: return
            when (action) {
                RecoveryAction.RetryAsHls -> {
                    asHls = true
                    open(channel)
                }

                RecoveryAction.RejoinLive -> {
                    exo?.seekToDefaultPosition()
                    exo?.prepare()
                }

                is RecoveryAction.Restart -> {
                    _state.update { it.copy(status = LiveState.Status.BUFFERING) }
                    handler.postDelayed({ open(channel) }, action.delayMs)
                }

                is RecoveryAction.GiveUp -> {
                    exo?.stop()
                    _state.update { it.copy(status = LiveState.Status.ERROR, error = action.error) }
                }
            }
        }

        // Time the current channel was opened: "first frame after N ms" in logcat (tag OntelevisPlayer),
        // to measure zapping speed.
        private var openedAt = 0L

        private val listener =
            object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    Log.d(LOG_TAG, "first frame after ${SystemClock.elapsedRealtime() - openedAt} ms")
                }

                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    if (isPlaying && openedAt > 0) {
                        Log.d(LOG_TAG, "playing after ${SystemClock.elapsedRealtime() - openedAt} ms")
                        openedAt = 0
                    }
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    when (playbackState) {
                        Player.STATE_READY -> {
                            policy.onReady()
                            _state.update { it.copy(status = LiveState.Status.PLAYING, error = null) }
                        }

                        Player.STATE_BUFFERING -> {
                            _state.update { it.copy(status = LiveState.Status.BUFFERING) }
                        }

                        // A live stream has no end: the server closed it, reconnect.
                        Player.STATE_ENDED -> {
                            apply(policy.onLiveEnded(SystemClock.elapsedRealtime()))
                        }

                        else -> {}
                    }
                }

                override fun onPlayerError(error: PlaybackException) {
                    apply(policy.onFailure(error.kind(), asHls, SystemClock.elapsedRealtime()))
                }

                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    _state.update { it.copy(videoHeight = videoSize.height) }
                }
            }
    }

@OptIn(UnstableApi::class)
internal fun PlaybackException.kind(): FailureKind {
    val http =
        generateSequence<Throwable>(this) {
            it.cause
        }.filterIsInstance<HttpDataSource.InvalidResponseCodeException>().firstOrNull()
    return when {
        http != null && (http.responseCode == 404 || http.responseCode == 410) -> FailureKind.GONE

        errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> FailureKind.BEHIND_LIVE_WINDOW

        errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES ||
            errorCode == PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED ||
            errorCode == PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ||
            errorCode == PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED ||
            (errorCode == PlaybackException.ERROR_CODE_DECODING_FAILED && isFormatAboveDevice()) -> FailureKind.DECODER

        errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED ||
            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED ||
            errorCode == PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED -> FailureKind.FORMAT

        else -> FailureKind.OTHER
    }
}

/** A decoding failure on a format the renderer had flagged as beyond this device. */
@OptIn(UnstableApi::class)
private fun PlaybackException.isFormatAboveDevice(): Boolean =
    (this as? androidx.media3.exoplayer.ExoPlaybackException)?.let {
        it.type == androidx.media3.exoplayer.ExoPlaybackException.TYPE_RENDERER &&
            it.rendererFormatSupport != androidx.media3.common.C.FORMAT_HANDLED
    } == true

private fun String.isHlsUrl(): Boolean = substringBefore('?').endsWith(".m3u8", ignoreCase = true)

private const val LOG_TAG = "OntelevisPlayer"
