package app.ontelevis.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.R
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.epg.ProgramState
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter

// ---------- Static pieces ----------

/** Outlined tag: 4K, HDR, PPV, 24/7. */
@Composable
fun OntTag(
    label: String,
    modifier: Modifier = Modifier,
    onFocusedParent: Boolean = false,
) {
    val c = OntTheme.colors
    val fg = if (onFocusedParent) c.onFocusMuted else c.textSecondary
    val stroke = if (onFocusedParent) c.onFocusMuted.copy(alpha = 0.4f) else c.lineStrong
    Text(
        text = label,
        style = OntTheme.type.tag,
        color = fg,
        modifier =
            modifier
                .border(OntDimens.Hairline, stroke, RoundedCornerShape(OntDimens.RadiusTag))
                .padding(horizontal = 7.dp, vertical = 2.dp),
    )
}

/** "EN DIRECT" badge. Translucent over video. */
@Composable
fun LiveBadge(
    modifier: Modifier = Modifier,
    overVideo: Boolean = false,
    label: String = stringResource(R.string.ont_live),
) {
    val c = OntTheme.colors
    Row(
        modifier =
            modifier
                .height(22.dp)
                .background(if (overVideo) c.background.copy(alpha = 0.72f) else c.surface2, RoundedCornerShape(6.dp))
                .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(Modifier.size(6.dp).background(c.playing, CircleShape))
        Text(label, style = OntTheme.type.tag, color = c.textPrimary)
    }
}

/** Uppercase section label ("RACCOURCIS", "CATÉGORIES"). */
@Composable
fun SectionLabel(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text.uppercase(),
        style = OntTheme.type.sectionLabel,
        color = OntTheme.colors.textTertiary,
        modifier = modifier,
    )
}

/** Square tile with the channel logo; the initials show while it loads, or when there is none. */
@Composable
fun ChannelLogoTile(
    initials: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = OntDimens.EpgChannelLogo,
    logoUrl: String? = null,
) {
    val c = OntTheme.colors
    val radius = if (size >= OntDimens.InfoLogoTile) 12.dp else OntDimens.RadiusTile
    val painter = rememberAsyncImagePainter(logoUrl)
    val loaded = logoUrl != null && painter.state.collectAsState().value is AsyncImagePainter.State.Success
    Box(
        modifier =
            modifier
                .size(size)
                .background(c.surface2, RoundedCornerShape(radius))
                .border(OntDimens.Hairline, c.tileBorder, RoundedCornerShape(radius)),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl != null) {
            Image(
                painter = painter,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(size / 8),
            )
        }
        if (!loaded) {
            Text(
                initials,
                style = OntTheme.type.logo.copy(fontSize = OntTheme.type.logo.fontSize * (size.value / 56f)),
                color = c.textMuted,
            )
        }
    }
}

// ---------- Focusable pieces ----------

/**
 * List item for the category panel and shortcuts.
 * States: idle (muted text) · selected (surface2 + amber dot) · focused (light surface, dark text, scale 1.04).
 */
@Composable
fun OntListItem(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: (@Composable (tint: Color) -> Unit)? = null,
    trailingTag: String? = null,
    /** Hold OK (remote) or long press (touch): usually opens a menu. */
    onLongClick: (() -> Unit)? = null,
    /** Shown faded (e.g. a hidden category while hidden ones are listed). */
    dimmed: Boolean = false,
    /** Padlock at the end (a category locked by the parental PIN). */
    locked: Boolean = false,
) {
    val c = OntTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interaction,
        modifier =
            modifier
                .fillMaxWidth()
                .height(OntDimens.ListItemHeight)
                .alpha(if (dimmed && !focused) 0.5f else 1f),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OntDimens.RadiusItem)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = if (selected) c.surface2 else Color.Transparent,
                contentColor = if (selected) c.textPrimary else c.textMuted,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
                pressedContainerColor = c.focusSurface,
                pressedContentColor = c.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_LIST),
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = OntDimens.ListItemPaddingH),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val dotColor =
                when {
                    !selected -> Color.Transparent
                    focused -> c.accentOnFocus
                    else -> c.accent
                }
            if (leadingIcon != null) {
                leadingIcon(
                    if (focused) {
                        c.onFocus
                    } else if (selected) {
                        c.textPrimary
                    } else {
                        c.textSecondary
                    },
                )
            } else {
                // Space is always reserved so the label never shifts when selection changes.
                Box(Modifier.size(OntDimens.SelectionDot).background(dotColor, CircleShape))
            }
            Text(
                text = text,
                style =
                    if (selected ||
                        focused
                    ) {
                        OntTheme.type.item.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
                    } else {
                        OntTheme.type.item
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (trailingTag != null) OntTag(trailingTag, onFocusedParent = focused)
            if (locked) {
                Icon(
                    painterResource(R.drawable.ont_ic_lock),
                    contentDescription = null,
                    tint = if (focused) c.onFocus else c.textMuted,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

/** Primary CTA: amber at rest, light surface when focused. */
@Composable
fun OntPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    val c = OntTheme.colors
    Button(
        onClick = onClick,
        modifier = modifier.height(OntDimens.ButtonHeight),
        shape = ButtonDefaults.shape(RoundedCornerShape(OntDimens.RadiusButton)),
        colors =
            ButtonDefaults.colors(
                containerColor = c.accent,
                contentColor = c.onAccent,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
                pressedContainerColor = c.focusSurface,
                pressedContentColor = c.onFocus,
            ),
        scale = ButtonDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_BUTTON),
        contentPadding = PaddingValues(horizontal = 18.dp),
        content = content,
    )
}

/** Square secondary icon button (Favori, Multivue). Always pass a contentDescription on the icon. */
@Composable
fun OntIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = OntTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.size(OntDimens.IconButton),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OntDimens.RadiusButton)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = c.surface1,
                contentColor = c.textSecondary,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border = Border(BorderStroke(OntDimens.Hairline, c.buttonBorder)),
                focusedBorder = Border.None,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_BUTTON),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
    }
}

/**
 * One EPG program block. Width/offset come from EpgWindow.spanOf() in the parent row.
 * [playing] = this is what the player shows (live dot). [progress] only for CURRENT.
 */
@Composable
fun EpgProgramBlock(
    title: String,
    subtitle: String,
    state: ProgramState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    progress: Float = 0f,
    onLongClick: (() -> Unit)? = null,
    /** A past programme that can be replayed (catch-up): rewind mark, brighter title. */
    replayable: Boolean = false,
) {
    val c = OntTheme.colors
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val (bg, titleColor, subColor) =
        when (state) {
            ProgramState.PAST -> {
                if (replayable) {
                    Triple(c.programPast, c.textSecondary, c.textTertiary)
                } else {
                    Triple(c.programPast, c.textTertiary, c.textTertiary)
                }
            }

            ProgramState.CURRENT -> {
                Triple(c.surface3, c.textPrimary, c.textMuted)
            }

            ProgramState.UPCOMING -> {
                Triple(c.programUpcoming, c.textSecondary, c.textTertiary)
            }
        }

    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        interactionSource = interaction,
        modifier = modifier.fillMaxHeight(),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OntDimens.RadiusBlock)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = bg,
                contentColor = titleColor,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
            ),
        border =
            ClickableSurfaceDefaults.border(
                border =
                    if (state ==
                        ProgramState.CURRENT
                    ) {
                        Border.None
                    } else {
                        Border(BorderStroke(OntDimens.Hairline, c.lineSubtle))
                    },
                focusedBorder = Border.None,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_BLOCK),
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (playing) {
                        Box(
                            Modifier.size(6.dp).background(if (focused) c.playingOnFocus else c.playing, CircleShape),
                        )
                    }
                    if (replayable) {
                        Icon(
                            painterResource(R.drawable.ont_ic_clock_counter_clockwise),
                            contentDescription = null,
                            tint = if (focused) c.onFocus else c.textMuted,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                    Text(
                        title,
                        style =
                            if (state == ProgramState.UPCOMING &&
                                !focused
                            ) {
                                OntTheme.type.itemStrong.copy(
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                                )
                            } else {
                                OntTheme.type.itemStrong
                            },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    subtitle,
                    style = OntTheme.type.meta,
                    color = if (focused) c.onFocusMuted else subColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state == ProgramState.CURRENT && progress > 0f && !focused) {
                BoxWithConstraints(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(OntDimens.ProgressBar)) {
                    Box(Modifier.width(maxWidth * progress.coerceIn(0f, 1f)).fillMaxHeight().background(c.accent))
                }
            }
        }
    }
}

/** Vertical "now" line + time pill, drawn above rows but below the focused block (zIndex 1 vs 2). */
@Composable
fun EpgNowLine(
    label: String,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    Box(modifier.fillMaxHeight()) {
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .width(OntDimens.NowLine)
                .fillMaxHeight()
                .background(c.accent),
        )
        Text(
            label,
            style = OntTheme.type.time.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
            color = c.onAccent,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 9.dp)
                    .background(c.accent, RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}
