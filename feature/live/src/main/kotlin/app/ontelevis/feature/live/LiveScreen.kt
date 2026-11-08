package app.ontelevis.feature.live

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
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
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import app.ontelevis.core.designsystem.component.OntPrimaryButton
import app.ontelevis.core.designsystem.component.OntTopBar
import app.ontelevis.core.designsystem.component.SectionLabel
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ChannelGroup
import app.ontelevis.core.model.Programme
import app.ontelevis.core.model.Shortcut
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
    /** Returns true when the channel is now a favourite. */
    val toggleFavorite: suspend (Channel) -> Boolean,
    val moveGroup: (ChannelGroup, Int) -> Unit,
    val setGroupHidden: (ChannelGroup, Boolean) -> Unit,
    val toggleShowHidden: () -> Unit,
    val clearHistory: () -> Unit,
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
            LiveActions(
                select = viewModel::select,
                play = viewModel::play,
                addPlaylist = onAddPlaylist,
                fullscreen = onOpenFullscreen,
                toggleFavorite = viewModel::toggleFavorite,
                moveGroup = viewModel::moveGroup,
                setGroupHidden = viewModel::setGroupHidden,
                toggleShowHidden = viewModel::toggleShowHidden,
                clearHistory = viewModel::clearHistory,
            )
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
        PhoneLive(state, channels.list, programmes, now, actions, modifier)
    }
}

@Composable
private fun groupLabel(group: ChannelGroup): String =
    when (group.shortcut) {
        Shortcut.FAVORITES -> stringResource(R.string.live_favorites)

        Shortcut.HISTORY -> stringResource(R.string.live_history)

        // Category names stay exactly as the provider wrote them (emoji and prefixes included).
        null -> group.name.ifEmpty { stringResource(R.string.live_uncategorized) }
    }

/** What to show instead of the channels, if anything. */
private enum class LiveStatus { LOADING, NO_PLAYLIST, NOT_LOADED, FAILED, READY }

private val LiveUiState.status: LiveStatus
    get() =
        when {
            playlists == null -> LiveStatus.LOADING
            playlists.isEmpty() -> LiveStatus.NO_PLAYLIST
            hasCategories -> LiveStatus.READY
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

/** One row of the categories panel. */
private sealed interface PanelEntry {
    val key: String

    data class Label(
        val text: Int,
    ) : PanelEntry {
        override val key get() = "label:$text"
    }

    data class Group(
        val group: ChannelGroup,
    ) : PanelEntry {
        override val key get() = group.entryKey
    }

    /** "Show / hide the hidden categories". */
    data class HiddenToggle(
        val count: Int,
        val showing: Boolean,
    ) : PanelEntry {
        override val key get() = "hidden-toggle"
    }
}

private val ChannelGroup.entryKey: String get() = "g:$playlistId/$name"

private fun panelEntries(state: LiveUiState): List<PanelEntry> =
    buildList {
        add(PanelEntry.Label(R.string.live_section_shortcuts))
        state.shortcuts.forEach { add(PanelEntry.Group(it)) }
        // With the shortcuts, not after the categories: big panels list hundreds of them.
        if (state.hiddenCount > 0) add(PanelEntry.HiddenToggle(state.hiddenCount, state.showHidden))
        add(PanelEntry.Label(R.string.live_section_categories))
        state.groups.forEach { add(PanelEntry.Group(it)) }
    }

@Composable
private fun TvLive(
    state: LiveUiState,
    channelsState: GroupChannels,
    guide: GuideData,
    live: LiveState,
    player: Player?,
    actions: LiveActions,
    modifier: Modifier,
) {
    val c = OntTheme.colors
    val context = LocalContext.current
    val title = stringResource(R.string.feature_live_title)
    val scope = rememberCoroutineScope()
    val channels = channelsState.list
    val panelFocus = remember { FocusRequester() }
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
    // Panel row holding the focus requester: the selected category unless another row needs focus.
    var panelFocusKey by remember { mutableStateOf<String?>(null) }
    var channelMenu by remember { mutableStateOf<Channel?>(null) }
    var groupMenu by remember { mutableStateOf<ChannelGroup?>(null) }
    // Category being moved with Up / Down (hold OK > Move).
    var moving by remember { mutableStateOf<ChannelGroup?>(null) }
    val entries = remember(state) { panelEntries(state) }
    val latestState by rememberUpdatedState(state)
    val latestEntries by rememberUpdatedState(entries)
    val latestChannels by rememberUpdatedState(channelsState)
    val latestLive by rememberUpdatedState(live)
    val playingRowVisible by remember(guideList) {
        derivedStateOf { guideList.layoutInfo.visibleItemsInfo.any { it.key == latestLive.channel?.id } }
    }

    // A lazy item off screen cannot take focus: scroll it in first.
    suspend fun focusPanel(key: String?) {
        val target = key ?: return
        panelFocusKey = target
        withFrameNanos { }
        val index = latestEntries.indexOfFirst { it.key == target }
        if (index < 0) return
        // Scroll only when the row is off screen: moving a list that already shows it looks like
        // focus landed somewhere else.
        val onScreen = categoryList.layoutInfo.visibleItemsInfo.any { it.index == index }
        if (!onScreen) {
            categoryList.scrollToItem((index - 2).coerceAtLeast(0))
            withFrameNanos { }
        }
        runCatching { panelFocus.requestFocus() }
    }

    suspend fun focusCategory() = focusPanel(latestState.selectedGroup?.entryKey)

    // The channel in the player first (its current programme), else the first row.
    suspend fun focusGuide() {
        val list = latestChannels.list
        if (list.isEmpty()) return focusCategory()
        val playingIndex = list.indexOfFirst { it.id == latestLive.channel?.id }
        if (playingIndex >= 0) {
            guideList.scrollToItem((playingIndex - 1).coerceAtLeast(0))
            withFrameNanos { }
            if (runCatching { playingRowFocus.requestFocus() }.isSuccess) return
        }
        withFrameNanos { }
        runCatching { guideEntry.requestFocus() }
    }

    // The remote must always have something focused: decide when the categories arrive.
    val ready = state.hasCategories
    LaunchedEffect(ready) {
        if (!ready) return@LaunchedEffect
        when (returnFocus) {
            ReturnFocus.GUIDE -> {
                returnFocus = ReturnFocus.NONE
                withTimeoutOrNull(2_000) { snapshotFlow { latestChannels.list }.first { it.isNotEmpty() } }
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
    LaunchedEffect(channelsState, guideFocusPending) {
        val group = latestState.selectedGroup ?: return@LaunchedEffect
        if (guideFocusPending && channelsState.groupKey == group.key) {
            // An empty shortcut has nothing to focus: stay on the category.
            if (channelsState.list.isNotEmpty()) focusGuide()
            // Reset last: this flag is a key of the effect, resetting it first cancelled the scroll above.
            guideFocusPending = false
        }
    }
    // The moved category keeps the focus at its new place.
    val movingKey = moving?.entryKey
    LaunchedEffect(movingKey, entries) {
        if (movingKey != null) focusPanel(movingKey)
    }
    BackHandler(enabled = moving != null) { moving = null }

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
            menu = { channelMenu = it },
        )

    Row(modifier.fillMaxSize().background(c.background)) {
        CategoryPanel(
            title = title,
            entries = entries,
            selected = state.selectedGroup,
            moving = moving,
            onSelect = { group ->
                actions.select(group)
                guideFocusPending = true
            },
            onMenu = { groupMenu = it },
            onMove = { step -> moving?.let { actions.moveGroup(it, step) } },
            onEndMove = {
                val done = moving
                moving = null
                scope.launch { focusPanel(done?.entryKey) }
            },
            onToggleHidden = actions.toggleShowHidden,
            // Right from a category: the channel in the player when its row is on screen (§6), else the
            // current programme of the first visible row.
            rightTarget = (if (playingRowVisible) playingRowFocus else guideEntry).takeIf { channels.isNotEmpty() },
            focusKey = panelFocusKey ?: state.selectedGroup?.entryKey,
            focusRequester = panelFocus,
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
                    val shortcut = state.selectedGroup?.shortcut
                    if (shortcut != null && channels.isEmpty() && channelsState.groupKey == groupKey) {
                        TvMessage(
                            stringResource(
                                if (shortcut ==
                                    Shortcut.FAVORITES
                                ) {
                                    R.string.live_favorites_empty
                                } else {
                                    R.string.live_history_empty
                                },
                            ),
                        )
                    } else {
                        EpgGuide(
                            channels = channels,
                            programmes = guide.programmes,
                            window = guide.window,
                            now = guide.now,
                            playingId = live.channel?.id,
                            favoriteIds = state.favoriteIds,
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

    channelMenu?.let { channel ->
        val favorite = channel.id in state.favoriteIds
        val added = stringResource(R.string.live_favorite_added)
        val removed = stringResource(R.string.live_favorite_removed)
        OntMenu(
            title = parseChannelName(channel.name).title,
            options =
                listOf(
                    OntMenuOption(
                        stringResource(
                            if (favorite) R.string.live_menu_remove_favorite else R.string.live_menu_add_favorite,
                        ),
                    ) {
                        scope.launch {
                            val inFavorites = state.selectedGroup?.shortcut == Shortcut.FAVORITES
                            val nowFavorite = actions.toggleFavorite(channel)
                            Toast.makeText(context, if (nowFavorite) added else removed, Toast.LENGTH_SHORT).show()
                            if (inFavorites && !nowFavorite) {
                                // Its row just left the Favourites list: focus the next one, or the category.
                                withTimeoutOrNull(1_000) {
                                    snapshotFlow { latestChannels.list }.first { list ->
                                        list.none { it.id == channel.id }
                                    }
                                }
                                guideList.scrollToItem(guideList.firstVisibleItemIndex)
                                focusGuide()
                            }
                        }
                    },
                ),
            onDismiss = { channelMenu = null },
        )
    }

    groupMenu?.let { group ->
        val options =
            when (group.shortcut) {
                Shortcut.HISTORY -> {
                    listOf(OntMenuOption(stringResource(R.string.live_menu_clear_history)) { actions.clearHistory() })
                }

                Shortcut.FAVORITES -> {
                    emptyList()
                }

                null -> {
                    listOf(
                        OntMenuOption(stringResource(R.string.live_menu_move)) { moving = group },
                        OntMenuOption(
                            stringResource(if (group.hidden) R.string.live_menu_unhide else R.string.live_menu_hide),
                        ) {
                            val hiding = !group.hidden
                            // A hidden category leaves the list (unless hidden ones are shown): its
                            // neighbour takes the focus.
                            val groups = state.groups
                            val index = groups.indexOfFirst { it.key == group.key }
                            val neighbour =
                                if (hiding && !state.showHidden) {
                                    (groups.getOrNull(index + 1) ?: groups.getOrNull(index - 1))?.entryKey
                                        ?: Shortcut.FAVORITES.let { ChannelGroup.of(it).entryKey }
                                } else {
                                    group.entryKey
                                }
                            actions.setGroupHidden(group, hiding)
                            scope.launch {
                                withTimeoutOrNull(1_000) {
                                    snapshotFlow { latestState.groups }.first { list ->
                                        list.none { it.key == group.key && it.hidden != hiding }
                                    }
                                }
                                focusPanel(neighbour)
                            }
                        },
                    )
                }
            }
        if (options.isEmpty()) {
            groupMenu = null
        } else {
            OntMenu(title = groupLabel(group), options = options, onDismiss = { groupMenu = null })
        }
    }
}

@Composable
private fun CategoryPanel(
    title: String,
    entries: List<PanelEntry>,
    selected: ChannelGroup?,
    moving: ChannelGroup?,
    onSelect: (ChannelGroup) -> Unit,
    onMenu: (ChannelGroup) -> Unit,
    onMove: (step: Int) -> Unit,
    onEndMove: () -> Unit,
    onToggleHidden: () -> Unit,
    rightTarget: FocusRequester?,
    focusKey: String?,
    focusRequester: FocusRequester,
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
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = OntDimens.SafeVertical),
        ) {
            items(entries, key = { it.key }) { entry ->
                val focusModifier =
                    Modifier
                        .focusProperties { if (rightTarget != null) right = rightTarget }
                        .then(if (entry.key == focusKey) Modifier.focusRequester(focusRequester) else Modifier)
                when (entry) {
                    is PanelEntry.Label -> {
                        SectionLabel(
                            stringResource(entry.text),
                            Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp),
                        )
                    }

                    is PanelEntry.Group -> {
                        val group = entry.group
                        val isMoving = moving?.key == group.key
                        OntListItem(
                            text = groupLabel(group),
                            selected = group.key == selected?.key,
                            onClick = { onSelect(group) },
                            onLongClick = { onMenu(group) }.takeIf { group.shortcut != Shortcut.FAVORITES },
                            dimmed = group.hidden,
                            trailingTag =
                                when {
                                    isMoving -> "▲▼"
                                    group.hidden -> stringResource(R.string.live_hidden_tag)
                                    else -> null
                                },
                            leadingIcon =
                                group.shortcut?.let { shortcut ->
                                    { tint ->
                                        Icon(
                                            painterResource(
                                                if (shortcut == Shortcut.FAVORITES) {
                                                    DesignR.drawable.ont_ic_star
                                                } else {
                                                    DesignR.drawable.ont_ic_clock_counter_clockwise
                                                },
                                            ),
                                            contentDescription = null,
                                            tint = tint,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                },
                            modifier =
                                focusModifier.then(
                                    if (isMoving) Modifier.moveKeys(onMove, onEndMove) else Modifier,
                                ),
                        )
                    }

                    is PanelEntry.HiddenToggle -> {
                        OntListItem(
                            text = stringResource(R.string.live_hidden_categories, entry.count),
                            // Highlighted while the hidden categories are listed (dimmed) below.
                            selected = entry.showing,
                            onClick = onToggleHidden,
                            leadingIcon = { tint ->
                                Icon(
                                    painterResource(DesignR.drawable.ont_ic_list_bullets),
                                    contentDescription = null,
                                    tint = tint,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                            modifier = focusModifier,
                        )
                    }
                }
            }
        }
    }
}

/** Move mode: Up / Down move the category, OK or Back end it, the other keys do nothing. */
private fun Modifier.moveKeys(
    onMove: (Int) -> Unit,
    onEnd: () -> Unit,
): Modifier =
    onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.DirectionUp -> {
                    onMove(-1)
                }

                Key.DirectionDown -> {
                    onMove(1)
                }

                Key.DirectionCenter, Key.Enter, Key.NumPadEnter, Key.Back, Key.Escape -> {
                    onEnd()
                }

                else -> {}
            }
        }
        true
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val added = stringResource(R.string.live_favorite_added)
    val removed = stringResource(R.string.live_favorite_removed)
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
                    items(state.shortcuts + state.groups, key = { "${it.playlistId}/${it.name}" }) { group ->
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
                                Modifier.combinedClickable(
                                    onClick = {
                                        actions.play(channel)
                                        actions.fullscreen()
                                    },
                                    // Long press: add to / remove from the favourites.
                                    onLongClick = {
                                        scope.launch {
                                            val favorite = actions.toggleFavorite(channel)
                                            Toast
                                                .makeText(context, if (favorite) added else removed, Toast.LENGTH_SHORT)
                                                .show()
                                        }
                                    },
                                ),
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
