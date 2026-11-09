package app.ontelevis.feature.live

import android.text.format.DateFormat
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import kotlinx.coroutines.launch
import java.time.Duration
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
    /** Hold OK on a block: the channel's menu, with that programme (null for a row without guide). */
    val menu: (Channel, Programme?) -> Unit,
    /** A block took the focus: its channel and programme (null: no guide for that channel). */
    val focused: (Channel, Programme?) -> Unit,
    /** OK on a past programme that can be replayed (catch-up). */
    val replay: (Channel, Programme) -> Unit,
)

/** Width of 30 minutes in the guide. The timeline is wider than the screen and scrolls. */
private val SLOT_WIDTH = 156.dp
private val SLOT_MS = 30L * 60 * 1000

/** Horizontal position of the timeline, shared by the header and every row. */
@Stable
private class Timeline(
    val total: Dp,
    viewport: Dp,
) {
    /** Visible width of the track. Changes with the screen layout, without rebuilding the timeline. */
    var viewport by mutableStateOf(viewport)

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
    /** Current time, read where needed: the minute tick does not recompose the whole guide. */
    now: () -> Instant,
    playingId: String?,
    favoriteIds: Set<String>,
    groupName: String,
    listState: LazyListState,
    entry: FocusRequester,
    playingRowFocus: FocusRequester,
    actions: GuideActions,
    modifier: Modifier = Modifier,
    /** Back from a catch-up recording: (channel id, programme start). The guide opens on it. */
    replayTarget: Pair<String, Long>? = null,
    replayFocus: FocusRequester? = null,
    /** Programme starts with a reminder, by channel id (bell on their blocks). */
    reminders: Map<String, Set<Long>> = emptyMap(),
) {
    val c = OntTheme.colors
    val times = rememberTimeFormatter()
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
        // When the window grows into the past (catch-up), the view stays on the same times: the scroll
        // moves by the width added on the left, instead of jumping back to "now".
        val previous = remember { arrayOfNulls<Pair<Instant, Timeline>>(1) }
        // Keyed on the window only: a width change (guide mode) updates the viewport below instead of
        // rebuilding the timeline, its scroll animation and every row on each animation frame.
        val timeline =
            remember(window.start, total) {
                val before = previous[0]
                Timeline(total, viewport)
                    .apply {
                        target =
                            if (before != null && !window.start.isAfter(before.first)) {
                                before.second.target +
                                    SLOT_WIDTH *
                                    (Duration.between(window.start, before.first).toMillis().toFloat() / SLOT_MS)
                            } else {
                                val at = replayTarget?.let { Instant.ofEpochMilli(it.second) } ?: now()
                                total * window.fractionOf(at) - SLOT_WIDTH / 3
                            }.coerceIn(0.dp, (total - viewport).coerceAtLeast(0.dp))
                    }.also { previous[0] = window.start to it }
            }
        SideEffect {
            if (timeline.viewport != viewport) {
                timeline.viewport = viewport
                timeline.target = timeline.target.coerceIn(0.dp, (total - viewport).coerceAtLeast(0.dp))
            }
        }
        // A new timeline starts where it is (no sweep across the added hours), then animates.
        val scrollAnim = remember(timeline) { Animatable(timeline.target, Dp.VectorConverter) }
        LaunchedEffect(timeline) {
            snapshotFlow { timeline.target }.collect { scrollAnim.animateTo(it, tween(220)) }
        }
        // Read during layout only (not composition): a scroll frame moves the blocks without
        // recomposing every row.
        val scroll: () -> Dp = remember(scrollAnim) { { scrollAnim.value } }
        // Up / Down: the block of the next channel at the same time (the current time when moving from
        // the programme on now). Plain focus search picked whichever block was geometrically closest.
        val rows = remember { mutableMapOf<Int, RowFocus>() }
        val anchor = remember { TimeAnchor() }
        val scope = rememberCoroutineScope()
        val vertical: (Int, Int, Long?) -> Boolean = { from, step, picked ->
            // The time chosen with Left / Right stays while moving up and down (null: the current time).
            val at = anchor.time ?: picked ?: now().toEpochMilli()
            val target = from + step
            if (target !in channels.indices) {
                false
            } else {
                scope.launch {
                    val visibleRows = listState.layoutInfo.visibleItemsInfo
                    val first = visibleRows.firstOrNull()?.index ?: 0
                    val last = visibleRows.lastOrNull()?.index ?: 0
                    // Keep the row fully on screen (the last visible one is often cut).
                    if (target < first || target >= last) {
                        listState.scrollToItem(
                            if (target <
                                first
                            ) {
                                target
                            } else {
                                (target - (last - first) + 1).coerceAtLeast(0)
                            },
                        )
                    }
                    repeat(8) { attempt ->
                        withFrameNanos { }
                        val row = rows[target]
                        val requester = row?.at(at)
                        anchor.movingVertically = true
                        if (requester != null && runCatching { requester.requestFocus() }.isSuccess) return@launch
                        anchor.movingVertically = false
                        // Its block may be outside the composed part of the row: bring that time into view.
                        if (attempt == 1) {
                            row?.spanAt(at)?.let { (s, e) ->
                                timeline.ensureVisible(
                                    total * window.fractionOf(Instant.ofEpochMilli(s)),
                                    total * window.fractionOf(Instant.ofEpochMilli(e)),
                                )
                            }
                        }
                    }
                }
                true
            }
        }
        Column(Modifier.fillMaxSize()) {
            GuideHeader(channels.size, window, now, times, total, scroll)
            Box(Modifier.fillMaxWidth().height(OntDimens.Hairline).background(c.line))
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(channels, key = { _, channel -> channel.id }) { index, channel ->
                    // Only this row's flag changes when the first visible row changes (not every row).
                    val isEntryRow by remember(index) { derivedStateOf { listState.firstVisibleItemIndex == index } }
                    GuideRow(
                        channel = channel,
                        programmes = programmes[channel.id].orEmpty(),
                        window = window,
                        now = now,
                        playing = channel.id == playingId,
                        favorite = channel.id in favoriteIds,
                        reminderStarts = reminders[channel.id].orEmpty(),
                        groupName = groupName,
                        times = times,
                        timeline = timeline,
                        scroll = scroll,
                        // Focus targets on the row's current programme: the channel in the player, and
                        // the first visible row (where the guide is entered from the categories).
                        playingFocus = playingRowFocus.takeIf { channel.id == playingId },
                        entryFocus = entry.takeIf { isEntryRow },
                        replayStart = replayTarget?.takeIf { it.first == channel.id }?.second,
                        replayFocus = replayFocus,
                        rowIndex = index,
                        rows = rows,
                        vertical = vertical,
                        anchor = anchor,
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
    scroll: () -> Dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(Modifier.weight(1f).fillMaxHeight().clipToBounds()) {
        Box(
            Modifier
                .wrapContentWidth(Alignment.Start, unbounded = true)
                .width(total)
                .fillMaxHeight()
                .offset { IntOffset(-scroll().roundToPx(), 0) },
            content = content,
        )
    }
}

@Composable
private fun GuideHeader(
    count: Int,
    window: EpgWindow,
    now: () -> Instant,
    times: DateTimeFormatter,
    total: Dp,
    scroll: () -> Dp,
) {
    val c = OntTheme.colors
    val zone = ZoneId.systemDefault()
    // The day shown is the one at the left edge of the view (the guide can scroll into yesterday).
    // The day changes rarely: only that change recomposes the header, not every scroll frame.
    val day by remember(window, total) {
        derivedStateOf {
            window.start
                .plusMillis((window.duration.toMillis() * (scroll() / total)).toLong())
                .atZone(zone)
                .toLocalDate()
        }
    }
    val locale = LocalConfiguration.current.locales[0]
    val today = LocalDate.now(zone)
    val dayLabel =
        when (day) {
            today -> stringResource(R.string.live_guide_today)
            today.minusDays(1) -> stringResource(R.string.live_guide_yesterday)
            else -> day.format(DateTimeFormatter.ofPattern("EEE d MMM", locale))
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
            val nowVisible by remember(window) {
                derivedStateOf {
                    val n = now()
                    !n.isBefore(window.start) && n.isBefore(window.end)
                }
            }
            if (nowVisible) {
                Text(
                    now().atZone(zone).format(times),
                    style = OntTheme.type.time.copy(fontWeight = FontWeight.Bold),
                    color = c.onAccent,
                    modifier =
                        Modifier
                            .align(Alignment.CenterStart)
                            .centeredAt { total * window.fractionOf(now()) }
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
    now: () -> Instant,
    playing: Boolean,
    favorite: Boolean,
    reminderStarts: Set<Long>,
    groupName: String,
    times: DateTimeFormatter,
    timeline: Timeline,
    scroll: () -> Dp,
    playingFocus: FocusRequester?,
    entryFocus: FocusRequester?,
    /** Start of the programme replayed on this row, to focus it again on return. */
    replayStart: Long?,
    replayFocus: FocusRequester?,
    rowIndex: Int,
    rows: MutableMap<Int, RowFocus>,
    vertical: (from: Int, step: Int, at: Long?) -> Boolean,
    anchor: TimeAnchor,
    actions: GuideActions,
) {
    val c = OntTheme.colors
    // Time at the left edge of the visible part (reference for programmes that began before it).
    val viewStartMillis = {
        window.start.toEpochMilli() + (window.duration.toMillis() * (scroll() / timeline.total)).toLong()
    }
    val parsed = remember(channel.name) { parseChannelName(channel.name) }
    val meta =
        remember(parsed, groupName) {
            listOfNotNull(parsed.group ?: groupName.ifEmpty { null }, parsed.tags.joinToString(" ").ifEmpty { null })
                .joinToString(" · ")
        }
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
                val single = remember { FocusRequester() }
                DisposableEffect(rowIndex, single) {
                    val focus = RowFocus(listOf(Long.MIN_VALUE to Long.MAX_VALUE), listOf(single))
                    rows[rowIndex] = focus
                    onDispose { if (rows[rowIndex] === focus) rows.remove(rowIndex) }
                }
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
                    progress = NO_PROGRESS,
                    x = 0.dp,
                    blockWidth = total,
                    timeline = timeline,
                    scroll = scroll,
                    first = true,
                    ownFocus = single,
                    playingFocus = playingFocus,
                    entryFocus = entryFocus,
                    replayFocus = null,
                    previous = null,
                    next = null,
                    onVertical = { step -> vertical(rowIndex, step, null) },
                    replayable = false,
                    onClick = { actions.open(channel) },
                    onLongClick = { actions.menu(channel, null) },
                    onFocused = {
                        anchor.focused(null)
                        actions.focused(channel, null)
                    },
                    actions = actions,
                )
            } else {
                // Left / Right move block by block in time order, Up / Down by time (RowFocus): plain
                // geometric focus search jumped to odd blocks around very short programmes.
                val blockFocus = remember(visible.size) { List(visible.size) { FocusRequester() } }
                DisposableEffect(rowIndex, visible, blockFocus) {
                    val focus = RowFocus(visible.map { (p, _) -> p.startMillis to p.stopMillis }, blockFocus)
                    rows[rowIndex] = focus
                    onDispose { if (rows[rowIndex] === focus) rows.remove(rowIndex) }
                }
                // The programme on now: changes when a programme ends, not at every minute.
                val currentIndex by remember(visible) {
                    derivedStateOf {
                        val n = now().toEpochMilli()
                        visible.indexOfFirst { (p, _) -> n >= p.startMillis && n < p.stopMillis }
                    }
                }
                // Only the blocks near the view are composed (a catch-up window can hold days of
                // programmes), plus each one's neighbours so Left / Right always reach the next block,
                // plus the row's focus targets (programme on now, replayed programme).
                val composed by remember(visible, timeline, total) {
                    derivedStateOf {
                        val lo = timeline.target - timeline.viewport
                        val hi = timeline.target + timeline.viewport * 2
                        val near =
                            visible.indices.filter { i ->
                                val (_, span) = visible[i]
                                total * span.second > lo && total * span.first < hi
                            }
                        buildSet {
                            near.forEach { i -> addAll((i - 1)..(i + 1)) }
                            add(currentIndex.takeIf { it >= 0 } ?: 0)
                            if (replayStart !=
                                null
                            ) {
                                add(visible.indexOfFirst { (p, _) -> p.startMillis == replayStart })
                            }
                        }
                    }
                }
                visible.forEachIndexed { index, (programme, span) ->
                    if (index !in composed) return@forEachIndexed
                    key(programme.startMillis) {
                        ProgrammeBlock(
                            channel = channel,
                            programme = programme,
                            index = index,
                            isLast = index == visible.lastIndex,
                            x = total * span.first,
                            blockWidth = total * (span.second - span.first),
                            now = now,
                            playing = playing,
                            isRowTarget = index == currentIndex || (index == 0 && currentIndex < 0),
                            times = times,
                            timeline = timeline,
                            scroll = scroll,
                            ownFocus = blockFocus[index],
                            playingFocus = playingFocus,
                            entryFocus = entryFocus,
                            replayFocus = replayFocus.takeIf { programme.startMillis == replayStart },
                            reminder = programme.startMillis in reminderStarts,
                            previous = blockFocus.getOrNull(index - 1),
                            next = blockFocus.getOrNull(index + 1),
                            onVertical = { step -> vertical(rowIndex, step, null) },
                            viewStartMillis = viewStartMillis,
                            anchor = anchor,
                            actions = actions,
                        )
                    }
                }
            }
            val nowVisible by remember(window) {
                derivedStateOf {
                    val n = now()
                    !n.isBefore(window.start) && n.isBefore(window.end)
                }
            }
            if (nowVisible) {
                // The now line sits above the rows but under the focused block (zIndex 1 vs 2). Placed
                // during layout: the minute tick moves it without recomposing the row.
                Box(
                    Modifier
                        .zIndex(1f)
                        .centeredAt { total * window.fractionOf(now()) }
                        .width(OntDimens.NowLine)
                        .fillMaxHeight()
                        .background(c.accent),
                )
            }
        }
    }
}

/** One programme of a guide row. Its state, "N min left" and progress follow [now] on their own. */
@Composable
private fun ProgrammeBlock(
    channel: Channel,
    programme: Programme,
    index: Int,
    isLast: Boolean,
    x: Dp,
    blockWidth: Dp,
    now: () -> Instant,
    playing: Boolean,
    isRowTarget: Boolean,
    times: DateTimeFormatter,
    timeline: Timeline,
    scroll: () -> Dp,
    ownFocus: FocusRequester,
    playingFocus: FocusRequester?,
    entryFocus: FocusRequester?,
    replayFocus: FocusRequester?,
    reminder: Boolean,
    previous: FocusRequester?,
    next: FocusRequester?,
    onVertical: (Int) -> Boolean,
    viewStartMillis: () -> Long,
    anchor: TimeAnchor,
    actions: GuideActions,
) {
    val start = remember(programme) { Instant.ofEpochMilli(programme.startMillis) }
    val end = remember(programme) { Instant.ofEpochMilli(programme.stopMillis) }
    val state by remember(programme) { derivedStateOf { programState(start, end, now()) } }
    val minutes by remember(programme) { derivedStateOf { minutesLeft(end, now()).toInt() } }
    // Catch-up: a past programme still inside the channel's archive can be replayed.
    val replayable by remember(programme, channel.catchupDays) {
        derivedStateOf {
            val n = now()
            !end.isAfter(n) &&
                channel.catchupDays > 0 &&
                !start.isBefore(n.minus(Duration.ofDays(channel.catchupDays.toLong())))
        }
    }
    val subtitle =
        if (state == ProgramState.CURRENT) {
            pluralStringResource(R.plurals.live_minutes_left, minutes, minutes)
        } else {
            remember(programme, times) {
                val zone = ZoneId.systemDefault()
                "${start.atZone(zone).format(times)} – ${end.atZone(zone).format(times)}"
            }
        }
    val progress = remember(programme) { { progressOf(start, end, now()) } }
    Block(
        title = programme.title,
        subtitle = subtitle,
        state = state,
        playing = playing && state == ProgramState.CURRENT,
        progress = progress,
        x = x,
        blockWidth = blockWidth,
        timeline = timeline,
        scroll = scroll,
        first = index == 0,
        ownFocus = ownFocus,
        // A row's focus targets sit on its programme on now (else its first block).
        playingFocus = playingFocus.takeIf { isRowTarget },
        entryFocus = entryFocus.takeIf { isRowTarget },
        replayFocus = replayFocus,
        previous = previous,
        next = next.takeIf { !isLast },
        onVertical = onVertical,
        replayable = replayable,
        reminder = reminder && state == ProgramState.UPCOMING,
        onClick = { if (replayable) actions.replay(channel, programme) else actions.open(channel) },
        onLongClick = { actions.menu(channel, programme) },
        onFocused = {
            // Chosen with Left / Right (or entering the guide): this programme's time becomes the
            // reference for Up / Down; the programme on now means "now".
            anchor.focused(
                if (state == ProgramState.CURRENT) null else maxOf(programme.startMillis, viewStartMillis()),
            )
            actions.focused(channel, programme)
        },
        actions = actions,
    )
}

@Composable
private fun Block(
    title: String,
    subtitle: String,
    state: ProgramState,
    playing: Boolean,
    progress: () -> Float,
    x: Dp,
    blockWidth: Dp,
    timeline: Timeline,
    scroll: () -> Dp,
    first: Boolean,
    ownFocus: FocusRequester,
    playingFocus: FocusRequester?,
    entryFocus: FocusRequester?,
    replayFocus: FocusRequester?,
    previous: FocusRequester?,
    next: FocusRequester?,
    /** Up (-1) / Down (+1): returns false to let the focus leave the guide (Up from the first row). */
    onVertical: (Int) -> Boolean,
    replayable: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: () -> Unit,
    actions: GuideActions,
    reminder: Boolean = false,
) {
    var focused by remember { mutableStateOf(false) }
    val inset = OntDimens.EpgBlockInsetH / 2
    val start = x + inset
    val end = (x + blockWidth - inset).coerceAtLeast(start + 6.dp)
    EpgProgramBlock(
        title = title,
        subtitle = subtitle,
        state = state,
        onClick = onClick,
        onLongClick = onLongClick,
        replayable = replayable,
        reminder = reminder,
        playing = playing,
        progress = progress,
        modifier =
            Modifier
                // Position from the scroll, computed during layout (no recomposition per scroll frame).
                // A programme that began before the visible edge keeps its title in view ("sticky"
                // left edge). Blocks keep their real width when focused: the info panel shows the rest.
                .layout { measurable, constraints ->
                    val viewStart = scroll() + inset
                    val viewEnd = scroll() + timeline.viewport - inset
                    val shownStart = maxOf(start, viewStart).coerceAtMost(end - 6.dp)
                    val width =
                        (
                            minOf(
                                end,
                                maxOf(viewEnd, shownStart + 6.dp),
                            ) - shownStart
                        ).roundToPx().coerceAtLeast(0)
                    val placeable = measurable.measure(constraints.copy(minWidth = width, maxWidth = width))
                    layout(width, placeable.height) { placeable.place(shownStart.roundToPx(), 0) }
                }.padding(vertical = OntDimens.EpgBlockInsetV)
                .zIndex(if (focused) 2f else 0f)
                .onFocusChanged {
                    focused = it.isFocused
                    // Bring the whole programme (as laid out in time) into view.
                    if (it.isFocused) {
                        timeline.ensureVisible(start, end)
                        onFocused()
                    }
                }.focusRequester(ownFocus)
                .then(if (playingFocus != null) Modifier.focusRequester(playingFocus) else Modifier)
                .then(if (entryFocus != null) Modifier.focusRequester(entryFocus) else Modifier)
                .then(if (replayFocus != null) Modifier.focusRequester(replayFocus) else Modifier)
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

                        event.key == Key.DirectionUp -> {
                            onVertical(-1)
                        }

                        event.key == Key.DirectionDown -> {
                            onVertical(1)
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

/** The time Up / Down aim at: set by the block focused with Left / Right, kept by Up / Down. */
private class TimeAnchor {
    /** Null: the current time (the user is on the programmes airing now). */
    var time: Long? = null
    var movingVertically = false

    fun focused(blockTime: Long?) {
        if (movingVertically) {
            movingVertically = false
        } else {
            time = blockTime
        }
    }
}

/** One guide row's blocks for Up / Down: their (start, stop) times and focus requesters. */
private class RowFocus(
    val spans: List<Pair<Long, Long>>,
    val requesters: List<FocusRequester>,
) {
    /** The block airing at [time]; else the next one; else the last. */
    fun at(time: Long): FocusRequester? = requesters.getOrNull(indexAt(time))

    /** (start, stop) of the block [at] picks. */
    fun spanAt(time: Long): Pair<Long, Long>? = spans.getOrNull(indexAt(time))

    private fun indexAt(time: Long): Int =
        spans.indexOfFirst { (start, stop) -> time in start until stop }.takeIf { it >= 0 }
            ?: spans.indexOfFirst { (start, _) -> start >= time }.takeIf { it >= 0 }
            ?: spans.lastIndex
}

/** Visible part of a programme in the window as (start, end) fractions, or null. */
private fun visibleSpan(
    programme: Programme,
    window: EpgWindow,
): Pair<Programme, Pair<Float, Float>>? =
    window.spanOf(Instant.ofEpochMilli(programme.startMillis), Instant.ofEpochMilli(programme.stopMillis))?.let {
        programme to it
    }

/** Places the element so that its horizontal centre is at [x]. */
private fun Modifier.centeredAt(x: Dp): Modifier = centeredAt { x }

/** Centered on [x], read during layout (a moving value does not recompose). */
private fun Modifier.centeredAt(x: () -> Dp): Modifier =
    layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        layout(placeable.width, placeable.height) {
            placeable.place(x().roundToPx() - placeable.width / 2, 0)
        }
    }

/** No progress bar (blocks without a programme). */
private val NO_PROGRESS: () -> Float = { 0f }

/** "14:30" or "2:30 PM", following the device's 12 / 24 h setting. */
@Composable
internal fun rememberTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = LocalConfiguration.current.locales[0]
    return remember(locale) {
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(context)) "HH:mm" else "h:mm a", locale)
    }
}
