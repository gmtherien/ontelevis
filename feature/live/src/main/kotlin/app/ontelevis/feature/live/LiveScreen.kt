package app.ontelevis.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.ChannelLogoTile
import app.ontelevis.core.designsystem.component.LocalIsTv
import app.ontelevis.core.designsystem.component.OntListItem
import app.ontelevis.core.designsystem.component.OntPrimaryButton
import app.ontelevis.core.designsystem.component.OntTopBar
import app.ontelevis.core.designsystem.component.SectionLabel
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ChannelGroup
import app.ontelevis.core.model.Programme
import app.ontelevis.core.model.epg.EpgWindow
import app.ontelevis.core.model.parseChannelName
import app.ontelevis.core.player.LiveState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.Instant
import androidx.compose.material3.Text as PhoneText
import app.ontelevis.core.designsystem.R as DesignR

/** What the TV screen can ask for. */
private class LiveActions(
    val select: (ChannelGroup) -> Unit,
    val play: (Channel) -> Unit,
    val addPlaylist: () -> Unit,
    val fullscreen: () -> Unit,
)

/**
 * The "Télé" screen. On TV: categories panel, preview + info panel, and (until the guide lands in
 * V1 step 4) the channel list of the selected category. OK on a channel plays it in the preview;
 * OK on the preview or "Full screen" opens [onOpenFullscreen].
 */
@Composable
fun LiveScreen(
    onAddPlaylist: () -> Unit,
    onOpenFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LiveViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val live by viewModel.live.collectAsStateWithLifecycle()
    val programmes by viewModel.programmes.collectAsStateWithLifecycle()
    val window by viewModel.window.collectAsStateWithLifecycle()
    val now by viewModel.now.collectAsStateWithLifecycle()
    val playingProgrammes by viewModel.playingProgrammes.collectAsStateWithLifecycle()
    val actions =
        remember(viewModel, onAddPlaylist, onOpenFullscreen) {
            LiveActions(viewModel::select, viewModel::play, onAddPlaylist, onOpenFullscreen)
        }
    if (LocalIsTv.current) {
        TvLive(
            state,
            channels,
            GuideData(programmes, window, now, playingProgrammes),
            live,
            viewModel.player,
            actions,
            modifier,
        )
    } else {
        PhoneLive(state, channels, programmes, now, actions, modifier)
    }
}

@Composable
private fun groupLabel(group: ChannelGroup): String =
    // Category names stay exactly as the provider wrote them (emoji and prefixes included).
    group.name.ifEmpty { stringResource(R.string.live_uncategorized) }

/** What to show instead of the channels, if anything. */
private enum class LiveStatus { LOADING, NO_PLAYLIST, NOT_LOADED, FAILED, READY }

private val LiveUiState.status: LiveStatus
    get() =
        when {
            playlists == null -> LiveStatus.LOADING
            playlists.isEmpty() -> LiveStatus.NO_PLAYLIST
            groups.isNotEmpty() -> LiveStatus.READY
            playlists.any { it.lastError != null } -> LiveStatus.FAILED
            else -> LiveStatus.NOT_LOADED
        }

/** Guide data for the TV screen. */
private class GuideData(
    val programmes: Map<String, List<Programme>>,
    val window: EpgWindow,
    val now: Instant,
    /** Now and next of the channel in the player. */
    val playing: List<Programme>,
)

/** Where focus goes back to after the full-screen player (§6: restore the previous element). */
private enum class ReturnFocus { NONE, GUIDE, PREVIEW }

@Composable
private fun TvLive(
    state: LiveUiState,
    channels: List<Channel>,
    guide: GuideData,
    live: LiveState,
    player: Player?,
    actions: LiveActions,
    modifier: Modifier,
) {
    val c = OntTheme.colors
    val title = stringResource(R.string.feature_live_title)
    val scope = rememberCoroutineScope()
    val categoryFocus = remember { FocusRequester() }
    val guideEntry = remember { FocusRequester() }
    val playingRowFocus = remember { FocusRequester() }
    val previewFocus = remember { FocusRequester() }
    val categoryList = rememberLazyListState()
    val groupKey = state.selectedGroup?.key
    // Each category opens at the top of its guide.
    val guideList = remember(groupKey) { LazyListState() }
    var returnFocus by rememberSaveable { mutableStateOf(ReturnFocus.NONE) }
    // OK on a category: focus follows into the guide once that category's channels are loaded.
    var guideFocusPending by remember { mutableStateOf(false) }
    val latestState by rememberUpdatedState(state)
    val latestChannels by rememberUpdatedState(channels)
    val latestLive by rememberUpdatedState(live)
    val playingRowVisible by remember(guideList) {
        derivedStateOf { guideList.layoutInfo.visibleItemsInfo.any { it.key == latestLive.channel?.id } }
    }

    // A lazy item off screen cannot take focus: scroll it in first.
    suspend fun focusCategory() {
        val s = latestState
        val index = s.groups.indexOfFirst { it.key == s.selectedGroup?.key }.coerceAtLeast(0)
        // Scroll only when the category is off screen: moving a list that already shows it looks like
        // focus landed somewhere else.
        val onScreen = categoryList.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!onScreen) {
            categoryList.scrollToItem((index - 2).coerceAtLeast(0))
            withFrameNanos { }
        }
        runCatching { categoryFocus.requestFocus() }
    }

    // The channel in the player first (its current programme), else the first row.
    suspend fun focusGuide() {
        val playingIndex = latestChannels.indexOfFirst { it.id == latestLive.channel?.id }
        if (playingIndex >= 0) {
            guideList.scrollToItem((playingIndex - 1).coerceAtLeast(0))
            withFrameNanos { }
            if (runCatching { playingRowFocus.requestFocus() }.isSuccess) return
        }
        withFrameNanos { }
        runCatching { guideEntry.requestFocus() }
    }

    // The remote must always have something focused: decide when the categories arrive.
    val hasGroups = state.groups.isNotEmpty()
    LaunchedEffect(hasGroups) {
        if (!hasGroups) return@LaunchedEffect
        when (returnFocus) {
            ReturnFocus.GUIDE -> {
                returnFocus = ReturnFocus.NONE
                withTimeoutOrNull(2_000) { snapshotFlow { latestChannels }.first { it.isNotEmpty() } }
                focusGuide()
            }

            ReturnFocus.PREVIEW -> {
                returnFocus = ReturnFocus.NONE
                withFrameNanos { }
                runCatching { previewFocus.requestFocus() }
            }

            ReturnFocus.NONE -> {
                // Let the startup resume pick its category first.
                delay(150)
                focusCategory()
            }
        }
    }
    LaunchedEffect(channels, guideFocusPending) {
        val group = latestState.selectedGroup ?: return@LaunchedEffect
        val loaded = channels.firstOrNull()?.let { it.playlistId == group.playlistId && it.group == group.name } == true
        if (guideFocusPending && loaded) {
            focusGuide()
            // Reset last: this flag is a key of the effect, resetting it first cancelled the scroll above.
            guideFocusPending = false
        }
    }

    val guideActions =
        GuideActions(
            open = { channel ->
                if (channel.id == live.channel?.id) {
                    // Second OK on the channel in the player: full screen.
                    returnFocus = ReturnFocus.GUIDE
                    actions.fullscreen()
                } else {
                    actions.play(channel)
                }
            },
            leftEdge = { scope.launch { focusCategory() } },
        )

    Row(modifier.fillMaxSize().background(c.background)) {
        CategoryPanel(
            title = title,
            groups = state.groups,
            selected = state.selectedGroup,
            onSelect = { group ->
                actions.select(group)
                guideFocusPending = true
            },
            // Right from a category: the channel in the player when its row is on screen (§6), else the
            // current programme of the first visible row.
            rightTarget = (if (playingRowVisible) playingRowFocus else guideEntry).takeIf { channels.isNotEmpty() },
            selectedFocus = categoryFocus,
            listState = categoryList,
            modifier = Modifier.width(OntDimens.CategoryPanelWidth).fillMaxHeight(),
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(
                        start = OntDimens.ContentStartPadding,
                        end = OntDimens.ContentEndPadding,
                        top = OntDimens.SafeVertical,
                        bottom = OntDimens.SafeVertical,
                    ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OntTopBar(listOfNotNull(title, state.selectedGroup?.let { groupLabel(it) }))
            when (state.status) {
                LiveStatus.LOADING -> {}

                LiveStatus.NO_PLAYLIST -> {
                    TvNoPlaylist(actions.addPlaylist)
                }

                LiveStatus.NOT_LOADED -> {
                    TvMessage(stringResource(R.string.live_loading))
                }

                LiveStatus.FAILED -> {
                    TvMessage(stringResource(R.string.live_failed))
                }

                LiveStatus.READY -> {
                    NowPlaying(
                        live = live,
                        player = player,
                        programmes = guide.playing,
                        now = guide.now,
                        previewFocus = previewFocus,
                        onFullscreen = {
                            if (live.channel != null) {
                                returnFocus = ReturnFocus.PREVIEW
                                actions.fullscreen()
                            }
                        },
                    )
                    EpgGuide(
                        channels = channels,
                        programmes = guide.programmes,
                        window = guide.window,
                        now = guide.now,
                        playingId = live.channel?.id,
                        groupName = state.selectedGroup?.let { groupLabel(it) }.orEmpty(),
                        listState = guideList,
                        entry = guideEntry,
                        playingRowFocus = playingRowFocus,
                        actions = guideActions,
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryPanel(
    title: String,
    groups: List<ChannelGroup>,
    selected: ChannelGroup?,
    onSelect: (ChannelGroup) -> Unit,
    rightTarget: FocusRequester?,
    selectedFocus: FocusRequester,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    Column(
        modifier =
            modifier
                .drawBehind {
                    drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                }.padding(start = 16.dp, end = 16.dp, top = OntDimens.SafeVertical),
    ) {
        Text(title, style = OntTheme.type.title, color = c.textPrimary, modifier = Modifier.padding(start = 10.dp))
        Spacer(Modifier.height(24.dp))
        if (groups.isNotEmpty()) {
            SectionLabel(stringResource(R.string.live_section_categories), Modifier.padding(start = 10.dp))
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = OntDimens.SafeVertical),
        ) {
            items(groups, key = { "${it.playlistId}/${it.name}" }) { group ->
                val isSelected = group.key == selected?.key
                OntListItem(
                    text = groupLabel(group),
                    selected = isSelected,
                    onClick = { onSelect(group) },
                    modifier =
                        Modifier
                            .focusProperties { if (rightTarget != null) right = rightTarget }
                            .then(if (isSelected) Modifier.focusRequester(selectedFocus) else Modifier),
                )
            }
        }
    }
}

@Composable
private fun TvNoPlaylist(onAddPlaylist: () -> Unit) {
    val c = OntTheme.colors
    val focus = remember { FocusRequester() }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(stringResource(R.string.live_no_playlist_title), style = OntTheme.type.title, color = c.textPrimary)
        Text(
            stringResource(R.string.live_no_playlist_body),
            style = OntTheme.type.body,
            color = c.textMuted,
            textAlign = TextAlign.Center,
        )
        OntPrimaryButton(onClick = onAddPlaylist, modifier = Modifier.focusRequester(focus)) {
            Icon(
                painterResource(DesignR.drawable.ont_ic_plus),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.live_add_playlist), style = OntTheme.type.button)
        }
    }
    LaunchedEffect(Unit) { focus.requestFocus() }
}

@Composable
private fun TvMessage(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = OntTheme.type.body, color = OntTheme.colors.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhoneLive(
    state: LiveUiState,
    channels: List<Channel>,
    programmes: Map<String, List<Programme>>,
    now: Instant,
    actions: LiveActions,
    modifier: Modifier,
) {
    Column(modifier.fillMaxSize()) {
        PhoneText(
            stringResource(R.string.feature_live_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(16.dp),
        )
        when (state.status) {
            LiveStatus.LOADING -> {}

            LiveStatus.NO_PLAYLIST -> {
                Column(
                    Modifier.fillMaxSize().padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    PhoneText(
                        stringResource(R.string.live_no_playlist_title),
                        style = MaterialTheme.typography.titleLarge,
                    )
                    PhoneText(stringResource(R.string.live_no_playlist_body), textAlign = TextAlign.Center)
                    Button(onClick = actions.addPlaylist) { PhoneText(stringResource(R.string.live_add_playlist)) }
                }
            }

            LiveStatus.NOT_LOADED -> {
                PhoneText(stringResource(R.string.live_loading), Modifier.padding(16.dp))
            }

            LiveStatus.FAILED -> {
                PhoneText(stringResource(R.string.live_failed), Modifier.padding(16.dp))
            }

            LiveStatus.READY -> {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.groups, key = { "${it.playlistId}/${it.name}" }) { group ->
                        FilterChip(
                            selected = group.key == state.selectedGroup?.key,
                            onClick = { actions.select(group) },
                            label = { PhoneText(groupLabel(group)) },
                        )
                    }
                }
                LazyColumn {
                    items(channels, key = { it.id }) { channel ->
                        val parsed = parseChannelName(channel.name)
                        ListItem(
                            modifier =
                                Modifier.clickable {
                                    actions.play(channel)
                                    actions.fullscreen()
                                },
                            headlineContent = { PhoneText(parsed.title) },
                            leadingContent = { PhoneText(channel.number.toString()) },
                            // What is on now, from the guide, else the quality tags.
                            supportingContent =
                                (
                                    onNow(programmes[channel.id], now)?.title
                                        ?: parsed.tags.joinToString(" ").ifEmpty { null }
                                )?.let { text -> { PhoneText(text, maxLines = 1) } },
                        )
                    }
                }
            }
        }
    }
}

/** The programme airing at [now], if the guide has one. */
internal fun onNow(
    programmes: List<Programme>?,
    now: Instant,
): Programme? {
    val millis = now.toEpochMilli()
    return programmes?.firstOrNull { it.startMillis <= millis && millis < it.stopMillis }
}
