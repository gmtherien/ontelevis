package app.ontelevis.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.ChannelLogoTile
import app.ontelevis.core.designsystem.component.LocalIsTv
import app.ontelevis.core.designsystem.component.OntImage
import app.ontelevis.core.designsystem.component.OntPosterCard
import app.ontelevis.core.designsystem.component.OntPrimaryButton
import app.ontelevis.core.designsystem.component.SectionLabel
import app.ontelevis.core.designsystem.component.touchClickable
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ContinueItem
import app.ontelevis.core.model.parseChannelName
import kotlinx.serialization.Serializable

/** Home: continue watching, favourites and what is new, across live, movies and series. */
@Serializable data object HomeRoute

private val POSTER_WIDTH = 118.dp

/**
 * Home screen. [onPlay]: resume a movie or episode; [onOpenMovie] / [onOpenSeries]: their pages.
 * A favourite channel plays in full screen (through the TV screen).
 */
@Composable
fun HomeScreen(
    onPlay: (contentId: String) -> Unit,
    onOpenMovie: (id: String) -> Unit,
    onOpenSeries: (id: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val rows = ui ?: return
    val first = remember { FocusRequester() }
    val tv = LocalIsTv.current
    // The first card takes the focus, so OK resumes what was watched last.
    LaunchedEffect(rows.isEmpty) { if (tv && !rows.isEmpty) runCatching { first.requestFocus() } }

    if (rows.isEmpty) {
        Box(modifier.fillMaxSize().padding(OntDimens.SafeHorizontal), contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.home_empty),
                style = OntTheme.type.body,
                color = OntTheme.colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val hero = rows.continueItems.firstOrNull()
    val posterRows =
        listOf(
            PosterRow(
                "fav_movies",
                R.string.home_favorite_movies,
                rows.favoriteMovies.map {
                    Poster(it.id, it.name, it.posterUrl, it.year)
                },
                onOpenMovie,
            ),
            PosterRow(
                "fav_series",
                R.string.home_favorite_series,
                rows.favoriteSeries.map {
                    Poster(it.id, it.name, it.posterUrl, it.year)
                },
                onOpenSeries,
            ),
            PosterRow(
                "new_movies",
                R.string.home_recent_movies,
                rows.recentMovies.map {
                    Poster(it.id, it.name, it.posterUrl, it.year)
                },
                onOpenMovie,
            ),
            PosterRow(
                "new_series",
                R.string.home_recent_series,
                rows.recentSeries.map {
                    Poster(it.id, it.name, it.posterUrl, it.year)
                },
                onOpenSeries,
            ),
        ).filter { it.posters.isNotEmpty() }
    // Where the focus starts: the Resume button, else the first card of the first row.
    val firstPosterRow = posterRows.firstOrNull()?.key.takeIf { hero == null && rows.channels.isEmpty() }
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = OntDimens.SafeVertical),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (hero != null) {
            item(key = "hero") {
                Hero(hero, onPlay = { onPlay(hero.contentId) }, modifier = Modifier.focusRequester(first))
            }
            item(key = "continue") {
                Row(stringResource(R.string.home_continue)) {
                    items(rows.continueItems, key = { it.contentId }) { item ->
                        OntPosterCard(
                            imageUrl = item.posterUrl,
                            title = item.title,
                            meta = item.episodeLabel(),
                            progress = item.progress,
                            onClick = { onPlay(item.contentId) },
                            // Hold OK: the movie's or series' page.
                            onLongClick = { item.seriesId?.let(onOpenSeries) ?: onOpenMovie(item.contentId) },
                            modifier = Modifier.width(POSTER_WIDTH),
                        )
                    }
                }
            }
        }
        if (rows.channels.isNotEmpty()) {
            item(key = "channels") {
                Row(stringResource(R.string.home_channels)) {
                    itemsIndexed(rows.channels, key = { _, it -> it.id }) { index, channel ->
                        ChannelCard(
                            channel,
                            onClick = { viewModel.playChannel(channel) },
                            modifier = if (hero == null && index == 0) Modifier.focusRequester(first) else Modifier,
                        )
                    }
                }
            }
        }
        posterRows.forEach { row ->
            item(key = row.key) {
                Row(stringResource(row.title)) {
                    itemsIndexed(row.posters, key = { _, it -> it.id }) { index, poster ->
                        OntPosterCard(
                            imageUrl = poster.imageUrl,
                            title = poster.title,
                            meta = poster.year?.toString(),
                            onClick = { row.onOpen(poster.id) },
                            modifier =
                                Modifier
                                    .width(POSTER_WIDTH)
                                    .then(
                                        if (row.key == firstPosterRow && index == 0) {
                                            Modifier.focusRequester(first)
                                        } else {
                                            Modifier
                                        },
                                    ),
                        )
                    }
                }
            }
        }
    }
}

private class PosterRow(
    val key: String,
    val title: Int,
    val posters: List<Poster>,
    val onOpen: (String) -> Unit,
)

private class Poster(
    val id: String,
    val title: String,
    val imageUrl: String?,
    val year: Int?,
)

/** A titled horizontal row. */
@Composable
private fun Row(
    title: String,
    content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column {
        SectionLabel(title, Modifier.padding(start = OntDimens.SafeHorizontal, top = 12.dp, bottom = 6.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = OntDimens.SafeHorizontal, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            content = content,
        )
    }
}

/** What was watched last, large, with a Resume button. */
@Composable
private fun Hero(
    item: ContinueItem,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    Box(Modifier.fillMaxWidth().height(HERO_HEIGHT)) {
        OntImage(item.backdropUrl ?: item.posterUrl, Modifier.fillMaxSize())
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(c.background, c.background.copy(alpha = 0.85f), Color.Transparent),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(Color.Transparent, c.background))),
        )
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = OntDimens.SafeHorizontal, bottom = 16.dp)
                .width(420.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                item.title,
                style = OntTheme.type.display,
                color = c.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.episodeLabel()?.let { Text(it, style = OntTheme.type.item, color = c.textSecondary, maxLines = 1) }
            Spacer(Modifier.height(4.dp))
            OntPrimaryButton(onClick = onPlay, modifier = modifier) {
                Text(stringResource(R.string.home_resume), style = OntTheme.type.itemStrong)
            }
        }
    }
}

/** A favourite channel: its logo on a 16:9 tile, the name under it. */
@Composable
private fun ChannelCard(
    channel: Channel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = OntTheme.colors
    val parsed = remember(channel.name) { parseChannelName(channel.name) }
    val shape = RoundedCornerShape(OntDimens.RadiusButton)
    Column(Modifier.width(CHANNEL_WIDTH), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Surface(
            onClick = onClick,
            modifier =
                Modifier
                    .touchClickable(onClick)
                    .then(modifier)
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            shape = ClickableSurfaceDefaults.shape(shape),
            colors = ClickableSurfaceDefaults.colors(containerColor = c.surface2, focusedContainerColor = c.surface2),
            border =
                ClickableSurfaceDefaults.border(
                    border = Border(BorderStroke(OntDimens.Hairline, c.tileBorder), shape = shape),
                    focusedBorder = Border(BorderStroke(2.dp, c.focusSurface), shape = shape),
                ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_LIST),
        ) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                ChannelLogoTile(parsed.initials, size = LOGO_SIZE, logoUrl = channel.logoUrl)
            }
        }
        Text(
            parsed.title,
            style = OntTheme.type.itemStrong,
            color = c.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ContinueItem.episodeLabel(): String? =
    seriesId?.let {
        listOfNotNull(stringResource(R.string.home_episode, season, episode), episodeTitle?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
    }

private val HERO_HEIGHT: Dp = 230.dp
private val CHANNEL_WIDTH: Dp = 150.dp
private val LOGO_SIZE: Dp = 56.dp
