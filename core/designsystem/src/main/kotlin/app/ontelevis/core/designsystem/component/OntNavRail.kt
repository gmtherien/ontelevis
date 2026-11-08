package app.ontelevis.core.designsystem.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme

data class OntRailItem(
    val key: Any,
    @param:DrawableRes val icon: Int,
    val label: String,
)

/**
 * TV navigation rail (§4 "Rail de navigation"): 72 dp of icons, expands to 240 dp with labels while
 * it holds focus. It draws over the content with a scrim, so the content never moves.
 *
 * [groups] are separated by a short line; [bottom] sits at the bottom (Settings).
 */
@Composable
fun OntNavRailScaffold(
    logo: String,
    groups: List<List<OntRailItem>>,
    bottom: OntRailItem?,
    selectedKey: Any?,
    onSelect: (OntRailItem) -> Unit,
    modifier: Modifier = Modifier,
    /** Focuses the rail (on its active item); lets the screen send Back up to the rail. */
    entryRequester: FocusRequester = remember { FocusRequester() },
    onRailFocusChanged: (Boolean) -> Unit = {},
    /** False on full-screen screens (the player): the content takes the whole screen. */
    railVisible: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    val c = OntTheme.colors
    var expanded by remember { mutableStateOf(false) }
    val width by animateDpAsState(
        targetValue = if (expanded) OntDimens.NavRailExpandedWidth else OntDimens.NavRailWidth,
        animationSpec = tween(OntMotion.FOCUS_ANIM_MS),
        label = "rail-width",
    )
    val selectedRequester = entryRequester

    Box(modifier.fillMaxSize().background(c.background)) {
        Box(
            Modifier.fillMaxSize().padding(start = if (railVisible) OntDimens.NavRailWidth else 0.dp),
            content = content,
        )
        if (!railVisible) return@Box
        if (expanded) {
            Box(Modifier.fillMaxSize().zIndex(1f).background(Color.Black.copy(alpha = 0.6f)))
        }
        Column(
            modifier =
                Modifier
                    .zIndex(2f)
                    .width(width)
                    .fillMaxHeight()
                    .background(c.background)
                    .drawBehind {
                        drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                    }.onFocusChanged {
                        expanded = it.hasFocus
                        onRailFocusChanged(it.hasFocus)
                    }
                    // Entering the rail lands on the last focused item, else the active destination.
                    .focusRestorer(selectedRequester)
                    .focusGroup()
                    .padding(horizontal = 12.dp, vertical = OntDimens.SafeVertical),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            RailLogo(logo)
            Spacer(Modifier.height(18.dp))
            groups.forEachIndexed { index, group ->
                if (index > 0) RailDivider()
                group.forEach { item ->
                    val selected = item.key == selectedKey
                    RailEntry(
                        item = item,
                        selected = selected,
                        expanded = expanded,
                        onClick = { onSelect(item) },
                        modifier = if (selected) Modifier.focusRequester(selectedRequester) else Modifier,
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            if (bottom != null) {
                val selected = bottom.key == selectedKey
                RailEntry(
                    item = bottom,
                    selected = selected,
                    expanded = expanded,
                    onClick = { onSelect(bottom) },
                    modifier = if (selected) Modifier.focusRequester(selectedRequester) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun RailLogo(letter: String) {
    val c = OntTheme.colors
    Box(
        modifier =
            Modifier
                .padding(start = 4.dp)
                .size(OntDimens.LogoSize)
                .background(c.accent, RoundedCornerShape(OntDimens.RadiusNavItem)),
        contentAlignment = Alignment.Center,
    ) {
        Text(letter, style = OntTheme.type.logo, color = c.onAccent)
    }
}

@Composable
private fun RailDivider() {
    Box(
        Modifier
            .padding(start = 10.dp, top = 6.dp, bottom = 6.dp)
            .width(28.dp)
            .height(OntDimens.Hairline)
            .background(OntTheme.colors.navDivider),
    )
}

@Composable
private fun RailEntry(
    item: OntRailItem,
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .then(if (expanded) Modifier.fillMaxWidth() else Modifier.width(OntDimens.NavItemWidth))
                .height(OntDimens.NavItemHeight),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OntDimens.RadiusNavItem)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) c.accent.copy(alpha = 0.12f) else Color.Transparent,
                contentColor = if (selected) c.accent else c.textNav,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
                pressedContainerColor = c.focusSurface,
                pressedContentColor = c.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                painterResource(item.icon),
                contentDescription = item.label,
                modifier = Modifier.size(OntDimens.NavIcon),
            )
            if (expanded) {
                Text(item.label, style = OntTheme.type.item, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
