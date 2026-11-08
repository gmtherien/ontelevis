package app.ontelevis.feature.live

import android.text.format.DateFormat
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.ChannelLogoTile
import app.ontelevis.core.designsystem.component.EpgProgramBlock
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.Programme
import app.ontelevis.core.model.epg.EpgWindow
import app.ontelevis.core.model.epg.ProgramState
import app.ontelevis.core.model.epg.minutesLeft
import app.ontelevis.core.model.epg.programState
import app.ontelevis.core.model.epg.progressOf
import app.ontelevis.core.model.epg.spanOf
import app.ontelevis.core.model.parseChannelName
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import app.ontelevis.core.designsystem.R as DesignR

/** What the guide reports back to the screen. */
internal class GuideActions(
    /** OK on a block: plays the channel, or opens full screen when it already plays. */
    val open: (Channel) -> Unit,
    /** Left from the first block of a row: back to the categories. */
    val leftEdge: () -> Unit,
    /** Hold OK on a block: the channel's menu (favourites). */
    val menu: (Channel) -> Unit,
)

/** Width of 30 minutes in the guide. The timeline is wider than the screen and scrolls. */
private val SLOT_WIDTH = 156.dp
private val SLOT_MS = 30L * 60 * 1000

/** Horizontal position of the timeline, shared by the header and every row. */
@Stable
private class Timeline(
    val total: Dp,
    val viewport: Dp,
) {
    /** Where the scroll is going (the animation follows it). */
    var target by mutableStateOf(0.dp)

    /** Scrolls the least needed so that [left]..[right] is on screen. */
    fun ensureVisible(
        left: Dp,
        right: Dp,
    ) {
        val margin = 24.dp
        val maxScroll = (total - viewport).coerceAtLeast(0.dp)
        val next =
            when {
                // Already fully shown, or a long programme that covers the whole view: stay.
                left >= target && right <= target + viewport -> target

                right - left > viewport && target in left..(right - viewport) -> target

                left < target -> left - margin

                else -> right - viewport + margin
            }
        target = next.coerceIn(0.dp, maxScroll)
    }
}

/**
 * §4 "Guide (EPG)": one LazyColumn whose items are whole rows (channel cell + time track), so
 * vertical focus and scrolling stay in one place. The time track is [SLOT_WIDTH] per 30 minutes
 * across the whole window (8 h), wider than the screen: header and rows scroll together to follow
 * the focused block. Up / Down use Compose's focus search; Left / Right go block by block.
 */
@Composable
internal fun EpgGuide(
    channels: List<Channel>,
    programmes: Map<String, List<Programme>>,
    window: EpgWindow,
    now: Instant,
    playingId: String?,
    favoriteIds: Set<String>,
    groupName: String,
    listState: LazyListState,
    entry: FocusRequester,
    playingRowFocus: FocusRequester,
    actions: GuideActions,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    val times = rememberTimeFormatter()
    val firstVisible by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex } }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(OntDimens.RadiusCard))
            .background(c.guideBackground),
    ) {
        val viewport = maxWidth - OntDimens.EpgChannelColumn - OntDimens.Hairline
        val total = SLOT_WIDTH * (window.duration.toMillis().toFloat() / SLOT_MS)
        // A new window opens with "now" a little inside the left edge (about 10 min of past shown),
        // so the now line and its time pill are never cut off.
        val timeline =
            remember(window.start, viewport, total) {
                Timeline(total, viewport).apply {
                    target =
                        (
                            total *
                                window.fractionOf(
                                    now,
                                ) - SLOT_WIDTH / 3
                        ).coerceIn(0.dp, (total - viewport).coerceAtLeast(0.dp))
                }
            }
        val scroll by animateDpAsState(timeline.target, tween(220), label = "guide-scroll")
        Column(Modifier.fillMaxSize()) {
            GuideHeader(channels.size, window, now, times, total, scroll)
            Box(Modifier.fillMaxWidth().height(OntDimens.Hairline).background(c.line))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    GuideRow(
                        channel = channel,
                        programmes = programmes[channel.id].orEmpty(),
                        window = window,
                        now = now,
                        playing = channel.id == playingId,
                        favorite = channel.id in favoriteIds,
                        groupName = groupName,
                        times = times,
                        timeline = timeline,
                        scroll = scroll,
                        // Focus targets on the row's current programme: the channel in the player, and
                        // the first visible row (where the guide is entered from the categories).
                        focusTargets =
                            listOfNotNull(
                                playingRowFocus.takeIf { channel.id == playingId },
                                entry.takeIf { index == firstVisible },
                            ),
                        actions = actions,
                    )
                }
            }
        }
    }
}

/** A box as wide as the whole timeline, shifted by the scroll and clipped to the visible part. */
@Composable
private fun RowScope.TimelineTrack(
    total: Dp,
    scroll: Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
        Box(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(total)
                .fillMaxHeight()
                .offset(x = -scroll),
            content = content,
        )
    }
}

@Composable
private fun GuideHeader(
    count: Int,
    window: EpgWindow,
    now: Instant,
    times: DateTimeFormatter,
    total: Dp,
    scroll: Dp,
) {
    val c = OntTheme.colors
    val zone = ZoneId.systemDefault()
    val day = window.start.atZone(zone).toLocalDate()
    val locale = LocalConfiguration.current.locales[0]
    val dayLabel =
        if (day == LocalDate.now(zone)) {
            stringResource(R.string.live_guide_today)
        } else {
            day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
        }
    Row(Modifier.fillMaxWidth().height(OntDimens.EpgHeaderHeight)) {
        Row(
            Modifier.width(OntDimens.EpgChannelColumn).fillMaxHeight().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(dayLabel, style = OntTheme.type.itemStrong, color = c.textPrimary)
            Text(
                pluralStringResource(R.plurals.live_channel_count, count, count),
                style = OntTheme.type.meta,
                color = c.textMuted,
                maxLines = 1,
            )
        }
        TimelineTrack(total, scroll) {
            window.ticks().forEach { tick ->
                Row(
                    Modifier.offset(x = total * window.fractionOf(tick)).fillMaxHeight(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.width(OntDimens.Hairline).fillMaxHeight().background(c.line))
                    Spacer(Modifier.width(10.dp))
                    Text(tick.atZone(zone).format(times), style = OntTheme.type.time, color = c.textTertiary)
                }
            }
            if (!now.isBefore(window.start) && now.isBefore(window.end)) {
                Text(
                    now.atZone(zone).format(times),
                    style = OntTheme.type.time.copy(fontWeight = FontWeight.Bold),
                    color = c.onAccent,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .centeredAt(total * window.fractionOf(now))
                            .zIndex(1f)
                            .background(c.accent, RoundedCornerShape(5.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun GuideRow(
    channel: Channel,
    programmes: List<Programme>,
    window: EpgWindow,
    now: Instant,
    playing: Boolean,
    favorite: Boolean,
    groupName: String,
    times: DateTimeFormatter,
    timeline: Timeline,
    scroll: Dp,
    focusTargets: List<FocusRequester>,
    actions: GuideActions,
) {
    val c = OntTheme.colors
    val parsed = remember(channel.name) { parseChannelName(channel.name) }
    val meta =
        listOfNotNull(parsed.group ?: groupName.ifEmpty { null }, parsed.tags.joinToString(" ").ifEmpty { null })
            .joinToString(" · ")
    val total = timeline.total
    Row(
        Modifier
            .fillMaxWidth()
            .height(OntDimens.EpgRowHeight)
            .background(if (playing) c.selectedRow else Color.Transparent)
            .drawBehind {
                drawLine(c.lineSubtle, Offset(0f, size.height), Offset(size.width, size.height), 1.dp.toPx())
            },
    ) {
        Row(
            Modifier.width(OntDimens.EpgChannelColumn).fillMaxHeight().padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                channel.number.toString(),
                style = OntTheme.type.time,
                color = c.textTertiary,
                maxLines = 1,
                // Wide enough for 5-digit numbers (big Xtream panels).
                modifier = Modifier.width(38.dp),
            )
            ChannelLogoTile(parsed.initials, logoUrl = channel.logoUrl)
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        parsed.title,
                        style = OntTheme.type.itemStrong,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (favorite) {
                        Icon(
                            painterResource(DesignR.drawable.ont_ic_star),
                            contentDescription = stringResource(R.string.live_favorite),
                            tint = c.accent,
                            modifier = Modifier.padding(start = 4.dp).size(12.dp),
                        )
                    }
                }
                if (meta.isNotEmpty()) {
                    Text(
                        meta,
                        style = OntTheme.type.meta,
                        color = c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Box(Modifier.width(OntDimens.Hairline).fillMaxHeight().background(c.line))
        TimelineTrack(total, scroll) {
            val visible = remember(programmes, window) { programmes.mapNotNull { p -> visibleSpan(p, window) } }
            if (visible.isEmpty()) {
                // No guide for this channel (or nothing in this window): one block over the visible part.
                val subtitle =
                    stringResource(if (programmes.isEmpty()) R.string.live_continuous_sub else R.string.live_no_info)
                Block(
                    title =
                        stringResource(
                            if (programmes.isEmpty()) R.string.live_continuous else R.string.live_no_info_title,
                        ),
                    subtitle = if (playing) "${stringResource(R.string.live_now_playing)} · $subtitle" else subtitle,
                    state = ProgramState.CURRENT,
                    playing = playing,
                    progress = 0f,
                    x = 0.dp,
                    blockWidth = total,
                    timeline = timeline,
                    scroll = scroll,
                    first = true,
                    focusTargets = focusTargets,
                    previous = null,
                    next = null,
                    onClick = { actions.open(channel) },
                    onLongClick = { actions.menu(channel) },
                    actions = actions,
                )
            } else {
                val zone = ZoneId.systemDefault()
                // Left / Right move block by block in time order. A focused block widens over its
                // neighbours, so geometric focus search would no longer find the next one.
                val blockFocus = remember(visible.size) { List(visible.size) { FocusRequester() } }
                val hasCurrent =
                    visible.any { (p, _) ->
                        programState(Instant.ofEpochMilli(p.startMillis), Instant.ofEpochMilli(p.stopMillis), now) ==
                            ProgramState.CURRENT
                    }
                visible.forEachIndexed { index, (programme, span) ->
                    key(programme.startMillis) {
                        val start = Instant.ofEpochMilli(programme.startMillis)
                        val end = Instant.ofEpochMilli(programme.stopMillis)
                        val state = programState(start, end, now)
                        val subtitle =
                            if (state == ProgramState.CURRENT) {
                                val left = minutesLeft(end, now).toInt()
                                pluralStringResource(R.plurals.live_minutes_left, left, left)
                            } else {
                                "${start.atZone(zone).format(times)} – ${end.atZone(zone).format(times)}"
                            }
                        // A row's focus target is its current programme (else its first block).
                        val isTarget = state == ProgramState.CURRENT || (index == 0 && !hasCurrent)
                        Block(
                            title = programme.title,
                            subtitle = subtitle,
                            state = state,
                            playing = playing && state == ProgramState.CURRENT,
                            progress = if (state == ProgramState.CURRENT) progressOf(start, end, now) else 0f,
                            x = total * span.first,
                            blockWidth = total * (span.second - span.first),
                            timeline = timeline,
                            scroll = scroll,
                            first = index == 0,
                            focusTargets = listOf(blockFocus[index]) + if (isTarget) focusTargets else emptyList(),
                            previous = blockFocus.getOrNull(index - 1),
                            next = blockFocus.getOrNull(index + 1),
                            onClick = { actions.open(channel) },
                            onLongClick = { actions.menu(channel) },
                            actions = actions,
                        )
                    }
                }
            }
            if (!now.isBefore(window.start) && now.isBefore(window.end)) {
                // The now line sits above the rows but under the focused block (zIndex 1 vs 2).
                Box(
                    Modifier
                        .zIndex(1f)
                        .centeredAt(total * window.fractionOf(now))
                        .width(OntDimens.NowLine)
                        .fillMaxHeight()
                        .background(c.accent),
                )
            }
        }
    }
}

@Composable
private fun Block(
    title: String,
    subtitle: String,
    state: ProgramState,
    playing: Boolean,
    progress: Float,
    x: Dp,
    blockWidth: Dp,
    timeline: Timeline,
    scroll: Dp,
    first: Boolean,
    focusTargets: List<FocusRequester>,
    previous: FocusRequester?,
    next: FocusRequester?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    actions: GuideActions,
) {
    var focused by remember { mutableStateOf(false) }
    val inset = OntDimens.EpgBlockInsetH / 2
    val start = x + inset
    val end = (x + blockWidth - inset).coerceAtLeast(start + 6.dp)
    // A programme that began before the visible edge keeps its title in view ("sticky" left edge).
    val viewStart = scroll + inset
    val viewEnd = scroll + timeline.viewport - inset
    val shownStart = maxOf(start, viewStart).coerceAtMost(end - 6.dp)
    val restWidth = minOf(end, maxOf(viewEnd, shownStart + 6.dp)) - shownStart
    // A short programme cannot show its title: when focused, the block widens just enough for its
    // title and times (capped), over its neighbours, and stays inside the visible part.
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val titleStyle = OntTheme.type.itemStrong
    val subtitleStyle = OntTheme.type.meta
    val contentWidth =
        if (!focused) {
            0.dp
        } else {
            remember(title, subtitle, playing) {
                with(density) {
                    val titleWidth =
                        measurer
                            .measure(title, titleStyle, maxLines = 1)
                            .size.width
                            .toDp()
                    val subtitleWidth =
                        measurer
                            .measure(subtitle, subtitleStyle, maxLines = 1)
                            .size.width
                            .toDp()
                    // 12 dp padding each side, plus the "playing" dot before the title.
                    maxOf(titleWidth + if (playing) 12.dp else 0.dp, subtitleWidth) + 24.dp + 2.dp
                }
            }
        }
    val targetWidth =
        if (focused) {
            maxOf(
                restWidth,
                minOf(contentWidth, EXPANDED_MAX_WIDTH, timeline.viewport - inset * 2),
            )
        } else {
            restWidth
        }
    val shownWidth by animateDpAsState(targetWidth, tween(OntMotion.FOCUS_ANIM_MS), label = "block-width")
    val left = shownStart.coerceAtMost(viewEnd - shownWidth).coerceAtLeast(minOf(shownStart, viewStart))
    EpgProgramBlock(
        title = title,
        subtitle = subtitle,
        state = state,
        onClick = onClick,
        onLongClick = onLongClick,
        playing = playing,
        progress = progress,
        modifier =
            Modifier
                .offset(x = left)
                .width(shownWidth)
                .padding(vertical = OntDimens.EpgBlockInsetV)
                .zIndex(if (focused) 2f else 0f)
                .onFocusChanged {
                    focused = it.isFocused
                    // Bring the whole programme (as laid out in time) into view.
                    if (it.isFocused) timeline.ensureVisible(start, end)
                }.then(focusTargets.fold(Modifier as Modifier) { m, r -> m.focusRequester(r) })
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when {
                        event.key == Key.DirectionLeft && previous != null -> {
                            runCatching { previous.requestFocus() }
                            true
                        }

                        event.key == Key.DirectionRight && next != null -> {
                            runCatching { next.requestFocus() }
                            true
                        }

                        event.key == Key.DirectionLeft && first -> {
                            actions.leftEdge()
                            true
                        }

                        // Last block of the row: Right never jumps to another channel.
                        event.key == Key.DirectionRight -> {
                            true
                        }

                        else -> {
                            false
                        }
                    }
                },
    )
}

/** Widest a focused short programme grows to show its title. */
private val EXPANDED_MAX_WIDTH = 280.dp

/** Visible part of a programme in the window as (start, end) fractions, or null. */
private fun visibleSpan(
    programme: Programme,
    window: EpgWindow,
): Pair<Programme, Pair<Float, Float>>? =
    window.spanOf(Instant.ofEpochMilli(programme.startMillis), Instant.ofEpochMilli(programme.stopMillis))?.let {
        programme to it
    }

/** Places the element so that its horizontal centre is at [x]. */
private fun Modifier.centeredAt(x: Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(x.roundToPx() - placeable.width / 2, 0)
        }
    }

/** "14:30" or "2:30 PM", following the device's 12 / 24 h setting. */
@Composable
internal fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
    }
}
