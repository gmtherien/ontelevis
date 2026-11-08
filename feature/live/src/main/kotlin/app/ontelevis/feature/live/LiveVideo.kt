package app.ontelevis.feature.live

import androidx.annotation.OptIn
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.parseChannelName
import app.ontelevis.core.player.LiveState
import app.ontelevis.core.player.PlaybackError

/** The video of the live player, letterboxed. Shows nothing until something was played. */
@OptIn(UnstableApi::class)
@Composable
internal fun LiveVideo(
    player: Player?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(androidx.compose.ui.graphics.Color.Black)) {
        if (player != null) {
            ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
    }
}

/** "Connecting…" or the error, centred over the video. Nothing while it plays. */
@Composable
internal fun LiveStatusOverlay(
    live: LiveState,
    modifier: Modifier = Modifier,
) {
    val text =
        when (live.status) {
            LiveState.Status.BUFFERING -> if (live.videoHeight == 0) stringResource(R.string.live_connecting) else null
            LiveState.Status.ERROR -> live.error?.let { stringResource(it.text) }
            else -> null
        } ?: return
    Box(modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = OntTheme.type.body,
            color = if (live.status == LiveState.Status.ERROR) OntTheme.colors.live else OntTheme.colors.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}

/** Quality tag: from the provider name ("FHD", "4K"), else from the decoded video height. */
internal fun qualityLabel(
    channel: Channel,
    videoHeight: Int,
): String? =
    parseChannelName(channel.name).tags.firstOrNull() ?: when {
        videoHeight >= 2_160 -> "4K"
        videoHeight >= 1_080 -> "FHD"
        videoHeight >= 720 -> "HD"
        videoHeight > 0 -> "SD"
        else -> null
    }

private val PlaybackError.text: Int
    @StringRes get() =
        when (this) {
            PlaybackError.NOT_FOUND -> R.string.live_error_not_found
            PlaybackError.UNSUPPORTED -> R.string.live_error_unsupported
            PlaybackError.UNREACHABLE -> R.string.live_error_unreachable
            PlaybackError.RESTART_LOOP -> R.string.live_error_restart_loop
            PlaybackError.DEVICE_CANNOT_DECODE -> R.string.live_error_device
        }
