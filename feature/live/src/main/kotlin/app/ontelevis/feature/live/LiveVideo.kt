package app.ontelevis.feature.live

import android.util.LruCache
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ParsedChannelName
import app.ontelevis.core.model.parseChannelName
import app.ontelevis.core.player.LiveState
import app.ontelevis.core.player.PlaybackError
import app.ontelevis.core.playerui.PlayerOptionsViewModel
import app.ontelevis.core.playerui.PlayerVideo

/** The video of the live player in the channel's picture format, with subtitles. Black until something plays. */
@Composable
internal fun LiveVideo(
    player: Player?,
    modifier: Modifier = Modifier,
    options: PlayerOptionsViewModel = hiltViewModel(),
) {
    val format by options.options.live.format
        .collectAsStateWithLifecycle()
    PlayerVideo(player, format, modifier)
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

// The info panel asks on every focus move in the guide: the name's regexes run once per channel.
private val parsedNames = LruCache<String, ParsedChannelName>(256)

private fun parsedName(name: String): ParsedChannelName =
    parsedNames.get(name) ?: parseChannelName(name).also { parsedNames.put(name, it) }

/** Quality tag: from the provider name ("FHD", "4K"), else from the decoded video height. */
internal fun qualityLabel(
    channel: Channel,
    videoHeight: Int,
): String? =
    parsedName(channel.name).tags.firstOrNull() ?: when {
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
