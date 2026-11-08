package app.ontelevis.feature.vod

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.OntImage
import app.ontelevis.core.designsystem.component.OntListItem
import app.ontelevis.core.designsystem.component.OntPrimaryButton
import app.ontelevis.core.designsystem.component.OntSecondaryButton
import app.ontelevis.core.designsystem.component.ProgressBar
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntMotion
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Episode
import app.ontelevis.core.model.WatchProgress
import java.util.Locale
import app.ontelevis.core.designsystem.R as DesignR

/** Backdrop behind a detail page, faded into the background on the left and at the bottom. */
@Composable
private fun Backdrop(url: String?) {
    val c = OntTheme.colors
    Box(Modifier.fillMaxSize()) {
        OntImage(url, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(c.background, c.background.copy(alpha = 0.85f), c.background.copy(alpha = 0.35f)),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, c.background))))
    }
}

@Composable
private fun PlayLabel(text: String) {
    Icon(painterResource(DesignR.drawable.ont_ic_play), contentDescription = null, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(8.dp))
    Text(text, style = OntTheme.type.button)
}

/** Movie page: poster, details, Play / Resume and From the beginning. */
@Composable
fun MovieScreen(
    onPlay: (id: String, fromStart: Boolean) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: MovieViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val movie = state.movie ?: return
    val details = state.details
    val c = OntTheme.colors
    val playFocus = remember { FocusRequester() }
    val resumeMs = state.progress?.takeIf { !it.finished && it.positionMs > 60_000 }?.positionMs
    Box(modifier.fillMaxSize().background(c.background)) {
        Backdrop(details?.backdropUrl)
        Row(
            Modifier.fillMaxSize().padding(
                horizontal = OntDimens.SafeHorizontal,
                vertical =
                    OntDimens.SafeVertical + 12.dp,
            ),
            horizontalArrangement = Arrangement.spacedBy(32.dp),
        ) {
            OntImage(
                movie.posterUrl,
                Modifier
                    .width(
                        190.dp,
                    ).height(285.dp)
                    .clip(RoundedCornerShape(OntDimens.RadiusCard))
                    .background(c.surface2),
                fallback = movie.name,
            )
            Column(Modifier.widthIn(max = 560.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (movie.group.isNotEmpty()) Text(movie.group, style = OntTheme.type.meta, color = c.textMuted)
                Text(
                    movie.name,
                    style = OntTheme.type.display,
                    color = c.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val meta =
                    listOfNotNull(
                        movie.year?.toString(),
                        details?.durationSecs?.takeIf { it > 0 }?.let { formatDuration(it * 1000L) },
                        movie.rating.takeIf { it > 0 }?.let { "★ " + String.format(Locale.ROOT, "%.1f", it) },
                        details?.genre?.ifBlank { null },
                    ).joinToString("  ·  ")
                if (meta.isNotEmpty()) Text(meta, style = OntTheme.type.item, color = c.textSecondary)
                details?.plot?.ifBlank { null }?.let {
                    Text(
                        it,
                        style = OntTheme.type.body,
                        color = c.textMuted,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                details?.cast?.ifBlank { null }?.let {
                    Text(
                        stringResource(R.string.vod_cast, it),
                        style = OntTheme.type.meta,
                        color = c.textTertiary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                details?.director?.ifBlank { null }?.let {
                    Text(
                        stringResource(R.string.vod_director, it),
                        style = OntTheme.type.meta,
                        color = c.textTertiary,
                        maxLines = 1,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OntPrimaryButton(
                        onClick = { onPlay(movie.id, false) },
                        modifier = Modifier.focusRequester(playFocus),
                    ) {
                        PlayLabel(
                            if (resumeMs !=
                                null
                            ) {
                                stringResource(R.string.vod_resume, formatDuration(resumeMs))
                            } else {
                                stringResource(R.string.vod_play)
                            },
                        )
                    }
                    if (resumeMs != null) {
                        OntSecondaryButton(onClick = { onPlay(movie.id, true) }) {
                            Text(stringResource(R.string.vod_from_start), style = OntTheme.type.button)
                        }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { runCatching { playFocus.requestFocus() } }
}

/** Series page: details, seasons, then that season's episodes (with resume bars). */
@Composable
fun SeriesScreen(
    onPlay: (episodeId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SeriesViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val series = state.series ?: return
    val c = OntTheme.colors
    val seasons =
        remember(state.episodes) {
            state.episodes
                .map { it.season }
                .distinct()
                .sorted()
        }
    // Continue where the user stopped, else the first episode.
    val continueEpisode =
        remember(state.episodes, state.progress) {
            state.episodes
                .filter { state.progress[it.id] != null }
                .maxByOrNull { state.progress[it.id]!!.positionMs.coerceAtLeast(1) }
                ?.let { last ->
                    if (state.progress[last.id]!!.finished) {
                        state.episodes.getOrNull(
                            state.episodes.indexOf(last) + 1,
                        )
                    } else {
                        last
                    }
                } ?: state.episodes.firstOrNull()
        }
    var season by rememberSaveable { mutableIntStateOf(-1) }
    val shownSeason = if (season in seasons) season else continueEpisode?.season ?: seasons.firstOrNull() ?: 0
    val playFocus = remember { FocusRequester() }
    Box(modifier.fillMaxSize().background(c.background)) {
        Backdrop(series.backdropUrl)
        Column(
            Modifier.fillMaxSize().padding(
                start = OntDimens.SafeHorizontal,
                end = OntDimens.SafeHorizontal,
                top = OntDimens.SafeVertical,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                OntImage(
                    series.posterUrl,
                    Modifier
                        .width(
                            120.dp,
                        ).height(180.dp)
                        .clip(RoundedCornerShape(OntDimens.RadiusCard))
                        .background(c.surface2),
                    fallback = series.name,
                )
                Column(Modifier.widthIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (series.group.isNotEmpty()) Text(series.group, style = OntTheme.type.meta, color = c.textMuted)
                    Text(
                        series.name,
                        style = OntTheme.type.display,
                        color = c.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val meta =
                        listOfNotNull(
                            series.year?.toString(),
                            series.rating.takeIf { it > 0 }?.let { "★ " + String.format(Locale.ROOT, "%.1f", it) },
                            series.genre?.ifBlank { null },
                        ).joinToString("  ·  ")
                    if (meta.isNotEmpty()) Text(meta, style = OntTheme.type.item, color = c.textSecondary)
                    series.plot?.ifBlank { null }?.let {
                        Text(
                            it,
                            style = OntTheme.type.body,
                            color = c.textMuted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (continueEpisode != null) {
                        val started = state.progress[continueEpisode.id]?.let { !it.finished } == true
                        OntPrimaryButton(
                            onClick = { onPlay(continueEpisode.id) },
                            modifier = Modifier.focusRequester(playFocus),
                        ) {
                            PlayLabel(
                                stringResource(
                                    if (started) R.string.vod_continue_episode else R.string.vod_play_episode,
                                    continueEpisode.season,
                                    continueEpisode.episode,
                                ),
                            )
                        }
                    }
                }
            }
            when {
                state.loading -> {
                    Text(stringResource(R.string.vod_loading_episodes), style = OntTheme.type.body, color = c.textMuted)
                }

                state.episodes.isEmpty() -> {
                    Text(stringResource(R.string.vod_no_episodes), style = OntTheme.type.body, color = c.textMuted)
                }

                else -> {
                    if (seasons.size > 1) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(seasons) { number ->
                                OntListItem(
                                    text = stringResource(R.string.vod_season, number),
                                    selected = number == shownSeason,
                                    onClick = { season = number },
                                    modifier = Modifier.width(120.dp),
                                )
                            }
                        }
                    }
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(bottom = OntDimens.SafeVertical),
                    ) {
                        items(state.episodes.filter { it.season == shownSeason }, key = { it.id }) { episode ->
                            EpisodeRow(episode, series.name, state.progress[episode.id]) { onPlay(episode.id) }
                        }
                    }
                }
            }
        }
    }
    val ready = continueEpisode != null
    LaunchedEffect(ready) { if (ready) runCatching { playFocus.requestFocus() } }
}

@Composable
private fun EpisodeRow(
    episode: Episode,
    seriesName: String,
    progress: WatchProgress?,
    onClick: () -> Unit,
) {
    val c = OntTheme.colors
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().widthIn(max = 860.dp).height(84.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(OntDimens.RadiusBlock)),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = c.surface1.copy(alpha = 0.85f),
                contentColor = c.textPrimary,
                focusedContainerColor = c.focusSurface,
                focusedContentColor = c.onFocus,
            ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = OntMotion.FOCUS_SCALE_BLOCK),
    ) {
        Row(
            Modifier.fillMaxSize().padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(
                        120.dp,
                    ).fillMaxHeight()
                    .clip(RoundedCornerShape(OntDimens.RadiusTile))
                    .background(c.surface2),
            ) {
                OntImage(episode.imageUrl, Modifier.fillMaxSize())
                progress?.fraction()?.let { ProgressBar(it, Modifier.align(Alignment.BottomStart)) }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "${episode.episode}. ${cleanEpisodeTitle(episode.title, seriesName)}",
                    style = OntTheme.type.itemStrong,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (episode.durationSecs >
                    0
                ) {
                    Text(formatDuration(episode.durationSecs * 1000L), style = OntTheme.type.meta)
                }
                episode.plot
                    ?.ifBlank {
                        null
                    }?.let { Text(it, style = OntTheme.type.meta, maxLines = 2, overflow = TextOverflow.Ellipsis) }
            }
        }
    }
}

/** "1 h 38" or "42 min". */
internal fun formatDuration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >=
        60
    ) {
        "${minutes / 60} h ${String.format(Locale.ROOT, "%02d", minutes % 60)}"
    } else {
        "$minutes min"
    }
}
