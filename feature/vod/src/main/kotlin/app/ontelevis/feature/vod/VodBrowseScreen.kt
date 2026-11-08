package app.ontelevis.feature.vod

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.LocalIsTv
import app.ontelevis.core.designsystem.component.OntListItem
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import app.ontelevis.core.designsystem.component.OntPosterCard
import app.ontelevis.core.designsystem.component.OntSecondaryButton
import app.ontelevis.core.designsystem.component.OntTextField
import app.ontelevis.core.designsystem.component.OntTopBar
import app.ontelevis.core.designsystem.component.SectionLabel
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.VodCategory
import app.ontelevis.core.model.VodKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import app.ontelevis.core.designsystem.R as DesignR

/**
 * Movies or series: categories on the left (provider order, names as written), posters on the
 * right. OK on a category moves focus to its posters; OK on a poster opens its page.
 */
@Composable
fun VodBrowseScreen(
    kind: VodKind,
    onOpen: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    /** True while the posters have the focus: the shell hides its rail so they get the width. */
    onPosterModeChange: (Boolean) -> Unit = {},
    viewModel: VodBrowseViewModel = hiltViewModel(key = kind.name),
) {
    LaunchedEffect(kind) { viewModel.setKind(kind) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val itemsState by viewModel.items.collectAsStateWithLifecycle()
    val items = itemsState.list
    val c = OntTheme.colors
    val tv = LocalIsTv.current
    val title = stringResource(if (kind == VodKind.MOVIE) R.string.vod_movies else R.string.vod_series)
    val categoryFocus = remember { FocusRequester() }
    val gridEntry = remember { FocusRequester() }
    val categoryList = rememberLazyListState()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val searching = query.trim().length >= 2
    var sortMenu by remember { mutableStateOf(false) }
    // Each category, and each search, opens at the top of its posters.
    val gridState =
        rememberSaveable(if (searching) itemsState.categoryKey else state.selected?.key, saver = LazyGridState.Saver) {
            LazyGridState()
        }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menuItem by remember { mutableStateOf<VodItem?>(null) }
    val searchFocus = remember { FocusRequester() }
    // Poster mode (like the guide): while the posters have the focus, the rail and the categories
    // slide away. A poster's menu keeps the mode.
    var gridFocused by remember { mutableStateOf(false) }
    val posterMode = tv && (gridFocused || menuItem != null)
    val latestOnPosterMode by rememberUpdatedState(onPosterModeChange)
    LaunchedEffect(posterMode) { latestOnPosterMode(posterMode) }
    DisposableEffect(Unit) { onDispose { latestOnPosterMode(false) } }
    val panelWidth by animateDpAsState(
        targetValue = if (posterMode) 0.dp else OntDimens.CategoryPanelWidth,
        animationSpec = tween(OntMotion.FOCUS_ANIM_MS),
        label = "vod-categories-width",
    )
    val latestItems by rememberUpdatedState(itemsState)

    // Category whose posters should take the focus once they are on screen (OK on a category).
    var gridFocusFor by remember { mutableStateOf<Pair<Long, String>?>(null) }
    val latestState by rememberUpdatedState(state)

    suspend fun focusCategory() {
        val s = latestState
        val index = s.categories.indexOfFirst { it.key == s.selected?.key }.coerceAtLeast(0)
        if (categoryList.layoutInfo.visibleItemsInfo.none { it.index == index }) {
            categoryList.scrollToItem((index - 2).coerceAtLeast(0))
            withFrameNanos { }
        }
        runCatching { categoryFocus.requestFocus() }
    }

    val returnFocus = remember { FocusRequester() }
    val restoreId = remember { viewModel.lastOpenedId }

    // Initial focus: back from a poster's page, that poster; otherwise the selected category.
    val hasCategories = state.hasProviderCategories
    val favoritesEmpty =
        state.selected?.isFavorites == true && items.isEmpty() && itemsState.categoryKey == state.selected?.key
    LaunchedEffect(hasCategories) {
        if (hasCategories && tv) {
            delay(100)
            val s = latestState
            val shown = itemsState
            val index =
                if (restoreId != null && shown.categoryKey == s.selected?.key) {
                    shown.list.indexOfFirst { it.id == restoreId }
                } else {
                    -1
                }
            if (index >= 0) {
                if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    gridState.scrollToItem(index)
                    withFrameNanos { }
                }
                if (runCatching { returnFocus.requestFocus() }.isSuccess) return@LaunchedEffect
            }
            focusCategory()
        }
    }
    // Wait for the selected category's own posters: the previous list is still shown for a moment,
    // and focusing one of its posters loses the focus when that list goes away.
    LaunchedEffect(itemsState, gridFocusFor) {
        val target = gridFocusFor ?: return@LaunchedEffect
        if (itemsState.categoryKey != target) return@LaunchedEffect
        if (items.isNotEmpty()) {
            withFrameNanos { }
            runCatching { gridEntry.requestFocus() }
        }
        gridFocusFor = null
    }

    Row(modifier.fillMaxSize().background(c.background)) {
        if (tv) {
            // The panel keeps its width and slides out to the left (its rows stay laid out, so the
            // focus can come back to the selected category at once).
            Box(Modifier.width(panelWidth).fillMaxHeight().clipToBounds()) {
                Column(
                    Modifier
                        .wrapContentWidth(Alignment.End, unbounded = true)
                        .width(OntDimens.CategoryPanelWidth)
                        .fillMaxHeight()
                        .drawBehind {
                            drawLine(c.line, Offset(size.width, 0f), Offset(size.width, size.height), 1.dp.toPx())
                        }.padding(start = 16.dp, end = 16.dp, top = OntDimens.SafeVertical),
                ) {
                    Text(
                        title,
                        style = OntTheme.type.title,
                        color = c.textPrimary,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    LazyColumn(
                        state = categoryList,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = OntDimens.SafeVertical),
                    ) {
                        itemsIndexed(state.categories, key = {
                            _,
                            it,
                            ->
                            "${it.playlistId}/${it.name}"
                        }) { index, category ->
                            val selected = category.key == state.selected?.key
                            // The "Categories" label sits above the first provider category (under Favourites).
                            val firstProvider =
                                !category.isFavorites && state.categories.getOrNull(index - 1)?.isFavorites != false
                            if (firstProvider) {
                                SectionLabel(
                                    stringResource(R.string.vod_categories),
                                    Modifier.padding(
                                        start = 10.dp,
                                        top = if (index > 0) 12.dp else 0.dp,
                                        bottom = 8.dp,
                                    ),
                                )
                            }
                            OntListItem(
                                leadingIcon =
                                    if (category.isFavorites) {
                                        { tint ->
                                            Icon(
                                                painterResource(DesignR.drawable.ont_ic_star),
                                                contentDescription = null,
                                                tint = tint,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    } else {
                                        null
                                    },
                                text = categoryLabel(category),
                                selected = selected,
                                onClick = {
                                    viewModel.select(category)
                                    viewModel.setQuery("")
                                    gridFocusFor = category.key
                                },
                                modifier =
                                    Modifier
                                        .focusProperties { if (items.isNotEmpty()) right = gridEntry }
                                        .then(if (selected) Modifier.focusRequester(categoryFocus) else Modifier),
                            )
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(
                    start = if (tv) OntDimens.ContentStartPadding else 16.dp,
                    end = if (tv) OntDimens.ContentEndPadding else 16.dp,
                    top = if (tv) OntDimens.SafeVertical else 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OntTopBar(
                listOfNotNull(
                    title,
                    if (searching) {
                        stringResource(R.string.vod_search_crumb, query.trim())
                    } else {
                        state.selected?.let { categoryLabel(it) }
                    },
                ),
            )
            if (hasCategories) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OntTextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        label = "",
                        placeholder =
                            stringResource(
                                when {
                                    kind == VodKind.MOVIE && tv -> R.string.vod_search_movies_tv
                                    kind == VodKind.MOVIE -> R.string.vod_search_movies
                                    tv -> R.string.vod_search_series_tv
                                    else -> R.string.vod_search_series
                                },
                            ),
                        imeAction = ImeAction.Search,
                        onLeft = { scope.launch { focusCategory() } },
                        modifier = Modifier.weight(1f, fill = false).width(380.dp).focusRequester(searchFocus),
                    )
                    OntSecondaryButton(onClick = { sortMenu = true }) {
                        Icon(
                            painterResource(DesignR.drawable.ont_ic_list_bullets),
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.vod_sort_button, stringResource(sort.label)),
                            style = OntTheme.type.button,
                        )
                    }
                }
            }
            if (!tv && hasCategories) PhoneCategories(state, viewModel::select)
            when {
                state.playlists == null -> {}

                state.playlists?.isEmpty() == true ||
                    (!hasCategories && state.playlists.orEmpty().all { it.vodLoaded }) -> {
                    Message(
                        stringResource(
                            if (kind ==
                                VodKind.MOVIE
                            ) {
                                R.string.vod_empty_movies
                            } else {
                                R.string.vod_empty_series
                            },
                        ),
                    )
                }

                !hasCategories -> {
                    Message(
                        stringResource(
                            if (kind ==
                                VodKind.MOVIE
                            ) {
                                R.string.vod_loading_movies
                            } else {
                                R.string.vod_loading_series
                            },
                        ),
                    )
                }

                searching && items.isEmpty() && itemsState.categoryKey == searchKey(query.trim()) -> {
                    Message(stringResource(R.string.vod_search_none, query.trim()))
                }

                favoritesEmpty -> {
                    Message(stringResource(R.string.vod_favorites_empty))
                }

                else -> {
                    Text(
                        pluralStringResource(R.plurals.vod_count, items.size, items.size),
                        style = OntTheme.type.meta,
                        color = c.textMuted,
                    )
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(if (tv) 118.dp else 104.dp),
                        state = gridState,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        contentPadding = PaddingValues(top = 6.dp, bottom = OntDimens.SafeVertical),
                        modifier = Modifier.onFocusChanged { gridFocused = it.hasFocus },
                    ) {
                        itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                            OntPosterCard(
                                imageUrl = item.posterUrl,
                                title = item.title,
                                meta = itemMeta(item),
                                progress = item.progress,
                                favorite = item.id in state.favoriteIds,
                                onLongClick = { menuItem = item },
                                onClick = {
                                    viewModel.lastOpenedId = item.id
                                    onOpen(item.id)
                                },
                                modifier =
                                    Modifier
                                        .then(
                                            if (item.id ==
                                                restoreId
                                            ) {
                                                Modifier.focusRequester(returnFocus)
                                            } else {
                                                Modifier
                                            },
                                        ).then(
                                            if (index ==
                                                gridState.firstVisibleItemIndex
                                            ) {
                                                Modifier.focusRequester(gridEntry)
                                            } else {
                                                Modifier
                                            },
                                        )
                                        // Left from the first column goes back to the selected category
                                        // (plain focus search picks whichever category is beside the poster).
                                        .onPreviewKeyEvent { event ->
                                            val firstColumn =
                                                gridState.layoutInfo.visibleItemsInfo
                                                    .firstOrNull { it.index == index }
                                                    ?.column == 0
                                            // Holding Up jumps to the search field (no climbing the whole grid).
                                            val heldUp =
                                                event.key == Key.DirectionUp &&
                                                    event.type == KeyEventType.KeyDown &&
                                                    event.nativeKeyEvent.repeatCount > 0
                                            when {
                                                tv && heldUp -> {
                                                    runCatching { searchFocus.requestFocus() }
                                                    true
                                                }

                                                tv && firstColumn && event.key == Key.DirectionLeft -> {
                                                    if (event.type == KeyEventType.KeyDown) {
                                                        scope.launch { focusCategory() }
                                                    }
                                                    true
                                                }

                                                else -> {
                                                    false
                                                }
                                            }
                                        },
                            )
                        }
                    }
                }
            }
        }
    }
    if (sortMenu) {
        OntMenu(
            title = stringResource(R.string.vod_sort_title),
            options =
                VodSort.entries.map { option ->
                    val label = stringResource(option.label)
                    OntMenuOption(if (option == sort) "✓  $label" else label) { viewModel.setSort(option) }
                },
            onDismiss = { sortMenu = false },
        )
    }
    // Back from the posters: the selected category (the search, if any, is emptied).
    BackHandler(enabled = searching || posterMode) {
        if (searching) viewModel.setQuery("")
        scope.launch { focusCategory() }
    }

    menuItem?.let { item ->
        val favorite = item.id in state.favoriteIds
        val added = stringResource(R.string.vod_favorite_added)
        val removed = stringResource(R.string.vod_favorite_removed)
        OntMenu(
            title = item.title,
            options =
                listOf(
                    OntMenuOption(
                        stringResource(
                            if (favorite) R.string.vod_menu_remove_favorite else R.string.vod_menu_add_favorite,
                        ),
                    ) {
                        scope.launch {
                            val inFavorites = latestState.selected?.isFavorites == true
                            val nowFavorite = viewModel.toggleFavorite(item.id)
                            Toast.makeText(context, if (nowFavorite) added else removed, Toast.LENGTH_SHORT).show()
                            if (inFavorites && !nowFavorite) {
                                // Its poster just left the Favourites grid: focus the first one left, or the category.
                                withTimeoutOrNull(1_000) {
                                    snapshotFlow { latestItems.list }.first { list -> list.none { it.id == item.id } }
                                }
                                withFrameNanos { }
                                if (latestItems.list.isEmpty() || runCatching { gridEntry.requestFocus() }.isFailure) {
                                    focusCategory()
                                }
                            }
                        }
                    },
                ),
            onDismiss = { menuItem = null },
        )
    }
}

/** Raw provider names, like the live categories (emoji and prefixes kept). */
@Composable
private fun categoryLabel(category: VodCategory): String =
    if (category.isFavorites) {
        stringResource(R.string.vod_favorites)
    } else {
        category.name.ifEmpty { stringResource(R.string.vod_uncategorized) }
    }

private fun itemMeta(item: VodItem): String? =
    listOfNotNull(
        item.year?.toString(),
        item.rating.takeIf { it > 0 }?.let { "★ " + String.format(Locale.ROOT, "%.1f", it) },
    ).joinToString(" · ").ifEmpty { null }

/** This playlist's movies and series were loaded at least once (empty then means "none"). */
private val app.ontelevis.core.model.Playlist.vodLoaded: Boolean get() = lastVodAt != null

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, style = OntTheme.type.body, color = OntTheme.colors.textMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun PhoneCategories(
    state: VodBrowseState,
    onSelect: (VodCategory) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(state.categories, key = { "${it.playlistId}/${it.name}" }) { category ->
            androidx.compose.material3.FilterChip(
                selected = category.key == state.selected?.key,
                onClick = { onSelect(category) },
                label = { androidx.compose.material3.Text(categoryLabel(category)) },
            )
        }
    }
}

private val VodSort.label: Int
    get() =
        when (this) {
            VodSort.PROVIDER -> R.string.vod_sort_provider
            VodSort.NEWEST -> R.string.vod_sort_newest
            VodSort.OLDEST -> R.string.vod_sort_oldest
            VodSort.A_TO_Z -> R.string.vod_sort_a_z
            VodSort.Z_TO_A -> R.string.vod_sort_z_a
            VodSort.RANDOM -> R.string.vod_sort_random
        }
