package app.ontelevis.core.player

import android.app.ActivityManager
import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Creates the app's ExoPlayer instances.
 *
 * - `EXTENSION_RENDERER_MODE_ON`: the FFmpeg audio renderer is used next to the platform
 *   decoders, so AC3 / E-AC3 5.1 plays on boxes without a Dolby licence. `PREFER` would
 *   software-decode plain AAC too, which costs CPU on weak boxes.
 * - Decoder fallback: if the first hardware decoder fails to start, try the next one.
 * - Low-RAM boxes (Fire Stick, under 2.5 GB): the buffer is capped in bytes, or a 4K stream
 *   fills the heap and the app is killed.
 * - Redirects across http / https are followed (many IPTV panels redirect).
 */
class PlayerFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** [live]: start after ~1 s of buffer (zapping), instead of the defaults' 2.5 s. */
        @OptIn(UnstableApi::class)
        fun create(live: Boolean = false): ExoPlayer {
            val renderers =
                DefaultRenderersFactory(context)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true)
            val http =
                DefaultHttpDataSource
                    .Factory()
                    .setUserAgent(USER_AGENT)
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(30_000)
            val mediaSources = DefaultMediaSourceFactory(DefaultDataSource.Factory(context, http))
            val loadControl =
                DefaultLoadControl
                    .Builder()
                    .apply {
                        if (live) {
                            setBufferDurationsMs(
                                LIVE_MIN_BUFFER_MS,
                                LIVE_MAX_BUFFER_MS,
                                LIVE_START_BUFFER_MS,
                                LIVE_REBUFFER_MS,
                            )
                        }
                        if (lowRam) setTargetBufferBytes(LOW_RAM_BUFFER_BYTES)
                    }.build()
            val audio =
                AudioAttributes
                    .Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build()
            return ExoPlayer
                .Builder(context, renderers)
                .setMediaSourceFactory(mediaSources)
                .setLoadControl(loadControl)
                .setAudioAttributes(audio, true)
                .build()
        }

        // Asked once (it calls the system): players are created on the main thread.
        private val lowRam by lazy { isLowRam() }

        private fun isLowRam(): Boolean {
            val manager = context.getSystemService(ActivityManager::class.java) ?: return false
            val memory = ActivityManager.MemoryInfo().also(manager::getMemoryInfo)
            return manager.isLowRamDevice || memory.totalMem < LOW_RAM_TOTAL_BYTES
        }

        private companion object {
            const val USER_AGENT = "Ontelevis"
            const val LOW_RAM_TOTAL_BYTES = 2_500L * 1024 * 1024
            const val LOW_RAM_BUFFER_BYTES = 64 * 1024 * 1024

            // Live: playback starts after 1 s of buffer (2 s after a stall); keeps 15-30 s ahead.
            const val LIVE_MIN_BUFFER_MS = 15_000
            const val LIVE_MAX_BUFFER_MS = 30_000
            const val LIVE_START_BUFFER_MS = 1_000
            const val LIVE_REBUFFER_MS = 2_000
        }
    }
