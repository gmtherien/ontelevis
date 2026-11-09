package app.ontelevis.core.playerui

import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.ContentFrame
import app.ontelevis.core.model.PictureFormat

/** The video of [player] in the chosen [format], with its subtitles. Black until something plays. */
@OptIn(UnstableApi::class)
@Composable
fun PlayerVideo(
    player: Player?,
    format: PictureFormat,
    modifier: Modifier = Modifier,
    subtitles: Boolean = true,
) {
    Box(modifier.background(Color.Black)) {
        if (player != null) {
            ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = format.contentScale)
            if (subtitles) Subtitles(player, Modifier.fillMaxSize())
        }
    }
}

private val PictureFormat.contentScale: ContentScale
    get() =
        when (this) {
            PictureFormat.FIT -> ContentScale.Fit
            PictureFormat.FILL -> ContentScale.FillBounds
            PictureFormat.ZOOM -> ContentScale.Crop
        }

/** Media3's subtitle renderer (styles, positions, CEA-608 captions), fed with the player's cues. */
@OptIn(UnstableApi::class)
@Composable
private fun Subtitles(
    player: Player,
    modifier: Modifier,
) {
    val context = LocalContext.current
    val view =
        remember(context) {
            SubtitleView(context).apply {
                // The system caption settings (size, colours), like other TV apps.
                setUserDefaultStyle()
                setUserDefaultTextSize()
            }
        }
    DisposableEffect(player, view) {
        view.setCues(player.currentCues.cues)
        val listener =
            object : Player.Listener {
                override fun onCues(cueGroup: CueGroup) = view.setCues(cueGroup.cues)
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            view.setCues(emptyList())
        }
    }
    AndroidView(factory = { view }, modifier = modifier)
}
