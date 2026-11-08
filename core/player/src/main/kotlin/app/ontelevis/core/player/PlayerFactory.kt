package app.ontelevis.core.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * Creates the app's ExoPlayer instances.
 *
 * - `EXTENSION_RENDERER_MODE_ON`: the FFmpeg audio renderer is used next to the platform
 *   decoders, so AC3 / E-AC3 5.1 plays on boxes without a Dolby licence. `PREFER` would
 *   software-decode plain AAC too, which costs CPU on weak boxes.
 * - Decoder fallback: if the first hardware decoder fails to start, try the next one.
 */
class PlayerFactory
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        @OptIn(UnstableApi::class)
        fun create(): ExoPlayer {
            val renderers =
                DefaultRenderersFactory(context)
                    .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                    .setEnableDecoderFallback(true)
            return ExoPlayer.Builder(context, renderers).build()
        }
    }
