package app.ontelevis.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme

/**
 * Progress of a panel sliding away: 0 = shown, 1 = hidden, animated when [hidden] changes.
 * [wide] is true while the content should use the layout without the panel: as soon as hiding
 * starts, and until showing ends. It only changes at those two moments, so the content is laid out
 * once per move instead of on every animation frame (which made guide mode stutter on TV boxes).
 */
class SlideState(
    val progress: Animatable<Float, *>,
    wide: State<Boolean>,
) {
    val wide: Boolean by wide
}

@Composable
fun rememberSlideState(hidden: Boolean): SlideState {
    val progress = remember { Animatable(if (hidden) 1f else 0f) }
    LaunchedEffect(hidden) { progress.animateTo(if (hidden) 1f else 0f, tween(OntMotion.FOCUS_ANIM_MS)) }
    val latestHidden by rememberUpdatedState(hidden)
    val wide = remember { derivedStateOf { latestHidden || progress.value > 0f } }
    return remember(progress) { SlideState(progress, wide) }
}

/**
 * A side panel ([panel], [panelWidth] wide, on the left) that slides out of the screen when [hidden],
 * and the [content] beside it. Only translations are animated (graphicsLayer): the content takes the
 * wide layout at once and is drawn shifted while the panel moves, so nothing is re-measured per frame.
 */
@Composable
fun OntSlidingPanelLayout(
    panelWidth: Dp,
    hidden: Boolean,
    modifier: Modifier = Modifier,
    panel: @Composable () -> Unit,
    content: @Composable () -> Unit,
) {
    val slide = rememberSlideState(hidden)
    Box(modifier.clipToBounds()) {
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = if (slide.wide) 0.dp else panelWidth)
                .graphicsLayer {
                    translationX = if (slide.wide) panelWidth.toPx() * (1f - slide.progress.value) else 0f
                },
        ) { content() }
        Box(
            Modifier
                .zIndex(1f)
                .width(panelWidth)
                .fillMaxHeight()
                .graphicsLayer { translationX = -size.width * slide.progress.value }
                .background(OntTheme.colors.background),
        ) { panel() }
    }
}
