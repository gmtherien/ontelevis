package app.ontelevis.feature.live

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.ChannelLogoTile
import app.ontelevis.core.designsystem.component.LiveBadge
import app.ontelevis.core.designsystem.component.OntTag
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Programme
import app.ontelevis.core.model.parseChannelName
import app.ontelevis.core.player.LiveState
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId

/**
 * Full-screen live player. Remote: Up / CH+ next channel, Down / CH- previous, OK shows the info
 * bar (or retries after an error), Last channel jumps back. The info bar hides after 3 s.
 */
@Composable
fun PlayerScreen(
    modifier: Modifier = Modifier,
    viewModel: PlayerViewModel = hiltViewModel(),
) {
    val live by viewModel.live.collectAsStateWithLifecycle()
    val programmes by viewModel.programmes.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    // Each change restarts the 3 s countdown of the info bar.
    var overlayTick by remember { mutableIntStateOf(1) }
    val overlayVisible = overlayTick > 0
    val channelId = live.channel?.id

    LaunchedEffect(channelId) { overlayTick = kotlin.math.abs(overlayTick) + 1 }
    LaunchedEffect(overlayTick) {
        if (overlayTick > 0) {
            delay(OntMotion.CONTROLS_HIDE_DELAY_MS)
            overlayTick = -overlayTick
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
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
            // The key handler must sit above the focused node in the chain, or it never sees the keys.
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.ChannelUp, Key.PageUp -> {
                        viewModel.zap(1)
                    }

                    Key.DirectionDown, Key.ChannelDown, Key.PageDown -> {
                        viewModel.zap(-1)
                    }

                    Key.LastChannel -> {
                        viewModel.lastChannel()
                    }

                    Key.DirectionCenter, Key.Enter, Key.NumPadEnter -> {
                        if (live.status == LiveState.Status.ERROR) viewModel.retry() else showOverlay()
                    }

                    Key.DirectionLeft, Key.DirectionRight, Key.Info -> {
                        showOverlay()
                    }

                    else -> {
                        return@onKeyEvent false
                    }
                }
                true
            }.focusRequester(focus)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { showOverlay() } },
    ) {
        LiveVideo(viewModel.player, Modifier.fillMaxSize())
        LiveStatusOverlay(live)
        AnimatedVisibility(
            visible = overlayVisible && live.channel != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            InfoBar(live, programmes)
        }
    }
}

@Composable
private fun InfoBar(
    live: LiveState,
    programmes: List<Programme>,
) {
    val channel = live.channel ?: return
    val c = OntTheme.colors
    val parsed = remember(channel.name) { parseChannelName(channel.name) }
    Column(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f))))
            .padding(
                start = OntDimens.SafeHorizontal,
                end = OntDimens.SafeHorizontal,
                bottom = OntDimens.SafeVertical,
                top = 48.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(channel.number.toString(), style = OntTheme.type.clock, color = c.textMuted)
            ChannelLogoTile(parsed.initials, size = OntDimens.InfoLogoTile, logoUrl = channel.logoUrl)
            Column(Modifier.weight(1f)) {
                parsed.group?.let { Text(it, style = OntTheme.type.meta, color = c.textMuted) }
                Text(
                    parsed.title,
                    style = OntTheme.type.display,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            LiveBadge(overVideo = true)
            qualityLabel(channel, live.videoHeight)?.let { OntTag(it) }
            Text(stringResource(R.string.live_zap_hint), style = OntTheme.type.meta, color = c.textTertiary)
        }
        val now = Instant.now()
        val current = onNow(programmes, now)
        val next = programmes.firstOrNull { it.startMillis >= (current?.stopMillis ?: now.toEpochMilli()) }
        val times = rememberTimeFormatter()
        val zone = ZoneId.systemDefault()

        fun range(p: Programme) =
            "${Instant.ofEpochMilli(p.startMillis).atZone(zone).format(times)} – " +
                Instant.ofEpochMilli(p.stopMillis).atZone(zone).format(times)
        if (current != null) {
            Text("${range(current)}  ${current.title}", style = OntTheme.type.item, color = c.textPrimary, maxLines = 1)
        }
        if (next != null) {
            Text(
                "${stringResource(R.string.live_next)} · ${range(next)}  ${next.title}",
                style = OntTheme.type.meta,
                color = c.textMuted,
                maxLines = 1,
            )
        }
    }
}
