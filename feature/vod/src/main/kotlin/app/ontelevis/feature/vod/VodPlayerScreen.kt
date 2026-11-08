package app.ontelevis.feature.vod

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.ContentFrame
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.player.PlaybackError
import app.ontelevis.core.player.VodState
import kotlinx.coroutines.delay
import java.util.Locale

/**
 * Movie / episode player. Remote: OK or Play/Pause pauses, Left / Right go 10 s back / forward
 * (held = repeats), Rewind / Fast-forward 30 s, Up / Down show the bar. The bar hides after 3 s
 * while playing and stays while paused. Back leaves (the resume point is saved).
 */
@OptIn(UnstableApi::class)
@Composable
fun VodPlayerScreen(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: VodPlayerViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val playback by viewModel.playback.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    var overlayTick by remember { mutableIntStateOf(1) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    val paused = !playback.playing && playback.status == VodState.Status.READY
    val overlayVisible = overlayTick > 0 || paused || playback.status != VodState.Status.READY

    LaunchedEffect(ui.missing) { if (ui.missing) onExit() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(Unit) {
        while (true) {
            position = viewModel.positionMs
            duration = viewModel.durationMs
            delay(500)
        }
    }
    LaunchedEffect(overlayTick) {
        if (overlayTick > 0) {
            delay(OntMotion.CONTROLS_HIDE_DELAY_MS)
            overlayTick = -overlayTick
        }
    }
    val view = LocalView.current
    DisposableEffect(view) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }

    fun showOverlay() {
        overlayTick = kotlin.math.abs(overlayTick) + 1
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // Before focusable(): a key handler placed after the focused node never sees the keys.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.MediaPlayPause, Key.Spacebar -> {
                        viewModel.togglePause()
                    }

                    Key.MediaPlay, Key.MediaPause -> {
                        viewModel.togglePause()
                    }

                    Key.DirectionLeft -> {
                        viewModel.seekBy(-SEEK_STEP_MS)
                    }

                    Key.DirectionRight -> {
                        viewModel.seekBy(SEEK_STEP_MS)
                    }

                    Key.MediaRewind -> {
                        viewModel.seekBy(-SEEK_LONG_MS)
                    }

                    Key.MediaFastForward -> {
                        viewModel.seekBy(SEEK_LONG_MS)
                    }

                    Key.DirectionUp, Key.DirectionDown, Key.Info -> {}

                    else -> {
                        return@onKeyEvent false
                    }
                }
                showOverlay()
                true
            }.focusRequester(focus)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { viewModel.togglePause() } },
    ) {
        viewModel.exoPlayer?.let { player ->
            ContentFrame(player = player, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        }
        StatusText(playback, ui.catchup, Modifier.align(Alignment.Center))
        AnimatedVisibility(
            visible = overlayVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            Overlay(ui, position, duration, paused)
        }
    }
}

@Composable
private fun StatusText(
    playback: VodState,
    catchup: Boolean,
    modifier: Modifier,
) {
    val c = OntTheme.colors
    val text =
        when (playback.status) {
            VodState.Status.BUFFERING -> {
                stringResource(R.string.vod_loading)
            }

            VodState.Status.ERROR -> {
                stringResource(
                    when (playback.error) {
                        PlaybackError.NOT_FOUND -> {
                            if (catchup) R.string.vod_error_catchup_missing else R.string.vod_error_not_found
                        }

                        PlaybackError.UNSUPPORTED -> {
                            R.string.vod_error_unsupported
                        }

                        PlaybackError.DEVICE_CANNOT_DECODE -> {
                            R.string.vod_error_device
                        }

                        else -> {
                            R.string.vod_error_unreachable
                        }
                    },
                )
            }

            else -> {
                null
            }
        } ?: return
    Text(
        text,
        style = OntTheme.type.body,
        color = if (playback.status == VodState.Status.ERROR) c.live else c.textSecondary,
        modifier =
            modifier
                .background(
                    Color.Black.copy(alpha = 0.6f),
                    RoundedCornerShape(8.dp),
                ).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun Overlay(
    ui: VodPlayerUiState,
    position: Long,
    duration: Long,
    paused: Boolean,
) {
    val c = OntTheme.colors
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.85f), Color.Transparent)))
                .padding(horizontal = OntDimens.SafeHorizontal, vertical = OntDimens.SafeVertical),
        ) {
            Text(
                ui.title,
                style = OntTheme.type.display,
                color = c.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            ui.subtitle?.let {
                Text(
                    it,
                    style = OntTheme.type.item,
                    color = c.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                .padding(
                    start = OntDimens.SafeHorizontal,
                    end = OntDimens.SafeHorizontal,
                    bottom = OntDimens.SafeVertical,
                    top = 40.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            BoxWithConstraints(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(c.lineStrong),
            ) {
                val fraction = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
                Box(Modifier.width(maxWidth * fraction).fillMaxHeight().background(c.accent))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${clock(position)} / ${if (duration > 0) clock(duration) else "--:--"}",
                    style = OntTheme.type.clock,
                    color = c.textPrimary,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(if (paused) R.string.vod_hint_paused else R.string.vod_hint_playing),
                    style = OntTheme.type.meta,
                    color = c.textTertiary,
                )
            }
        }
    }
}

/** 1:05:09 or 12:34. */
private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h >
        0
    ) {
        String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(Locale.ROOT, "%d:%02d", m, s)
    }
}

private const val SEEK_STEP_MS = 10_000L
private const val SEEK_LONG_MS = 30_000L
