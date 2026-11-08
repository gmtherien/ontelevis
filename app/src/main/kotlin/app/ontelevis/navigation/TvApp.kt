package app.ontelevis.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ontelevis.R
import app.ontelevis.core.data.sync.PlaylistSync
import app.ontelevis.core.designsystem.component.OntNavRailScaffold
import app.ontelevis.core.designsystem.component.OntRailItem
import app.ontelevis.core.model.VodKind
import app.ontelevis.feature.live.LiveScreen
import app.ontelevis.feature.live.PlayerScreen
import app.ontelevis.feature.settings.AddPlaylistScreen
import app.ontelevis.feature.settings.SettingsScreen
import app.ontelevis.feature.vod.MovieRoute
import app.ontelevis.feature.vod.MovieScreen
import app.ontelevis.feature.vod.MoviesRoute
import app.ontelevis.feature.vod.SeriesListRoute
import app.ontelevis.feature.vod.SeriesRoute
import app.ontelevis.feature.vod.SeriesScreen
import app.ontelevis.feature.vod.VodBrowseScreen
import app.ontelevis.feature.vod.VodPlayerRoute
import app.ontelevis.feature.vod.VodPlayerScreen
import app.ontelevis.core.designsystem.R as DesignR
import app.ontelevis.feature.live.R as LiveR
import app.ontelevis.feature.settings.R as SettingsR
import app.ontelevis.feature.vod.R as VodR

private enum class RailKey { TV, MOVIES, SERIES, UPDATE, SETTINGS }

private fun NavDestination.railKey(): RailKey =
    when {
        hasRoute(LiveRoute::class) || hasRoute(PlayerRoute::class) -> RailKey.TV
        hasRoute(MoviesRoute::class) || hasRoute(MovieRoute::class) -> RailKey.MOVIES
        hasRoute(SeriesListRoute::class) || hasRoute(SeriesRoute::class) -> RailKey.SERIES
        hasRoute(VodPlayerRoute::class) -> RailKey.MOVIES
        else -> RailKey.SETTINGS
    }

/** Screens where Back first goes up to the rail (the top of each rail destination). */
private fun NavDestination.isRailTop(): Boolean =
    hasRoute(LiveRoute::class) || hasRoute(MoviesRoute::class) || hasRoute(SeriesListRoute::class)

/** Full-screen players hide the rail. */
private fun NavDestination.isPlayer(): Boolean = hasRoute(PlayerRoute::class) || hasRoute(VodPlayerRoute::class)

/**
 * TV / D-pad shell: the navigation rail of the design handoff and the screens beside it.
 * V1 destinations: TV, Movies, Series, Update content, Settings.
 */
@Composable
fun TvApp() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val backStack by navController.currentBackStackEntryAsState()
    val destination = backStack?.destination
    val selected = destination?.railKey()
    val updating = stringResource(R.string.content_update_started)
    val railEntry = remember { FocusRequester() }
    var railFocused by remember { mutableStateOf(false) }
    // Guide mode on the TV screen: the guide has the focus and takes the whole width (rail hidden).
    var liveGuideMode by remember { mutableStateOf(false) }
    // Back climbs one level before leaving: from a destination's content up to the rail.
    BackHandler(enabled = !railFocused && destination?.isRailTop() == true) { runCatching { railEntry.requestFocus() } }

    OntNavRailScaffold(
        logo = "O",
        groups =
            listOf(
                listOf(
                    OntRailItem(
                        RailKey.TV,
                        DesignR.drawable.ont_ic_television,
                        stringResource(LiveR.string.feature_live_title),
                    ),
                    OntRailItem(
                        RailKey.MOVIES,
                        DesignR.drawable.ont_ic_film_strip,
                        stringResource(VodR.string.vod_movies),
                    ),
                    OntRailItem(
                        RailKey.SERIES,
                        DesignR.drawable.ont_ic_monitor_play,
                        stringResource(VodR.string.vod_series),
                    ),
                ),
                listOf(
                    OntRailItem(
                        RailKey.UPDATE,
                        DesignR.drawable.ont_ic_arrows_clockwise,
                        stringResource(R.string.content_update),
                    ),
                ),
            ),
        bottom =
            OntRailItem(
                RailKey.SETTINGS,
                DesignR.drawable.ont_ic_sliders_horizontal,
                stringResource(SettingsR.string.feature_settings_title),
            ),
        selectedKey = selected,
        entryRequester = railEntry,
        onRailFocusChanged = { railFocused = it },
        railVisible =
            destination?.isPlayer() != true && !(liveGuideMode && destination?.hasRoute(LiveRoute::class) == true),
        onSelect = { item ->
            val key = item.key as RailKey
            if (key == RailKey.UPDATE) {
                PlaylistSync.refreshNow(context)
                Toast.makeText(context, updating, Toast.LENGTH_SHORT).show()
            } else if (key == selected && destination.isRailTop() == true) {
                // Already there: OK moves on to the next pane, like everywhere else.
                focusManager.moveFocus(FocusDirection.Right)
            } else {
                navController.goTo(
                    when (key) {
                        RailKey.TV -> LiveRoute
                        RailKey.MOVIES -> MoviesRoute
                        RailKey.SERIES -> SeriesListRoute
                        else -> SettingsRoute
                    },
                )
            }
        },
    ) {
        NavHost(navController = navController, startDestination = LiveRoute) {
            composable<LiveRoute> {
                LiveScreen(
                    onAddPlaylist = { navController.navigate(AddPlaylistRoute) },
                    onOpenFullscreen = { navController.navigate(PlayerRoute) },
                    onGuideModeChange = { liveGuideMode = it },
                    onOpenCatchup = { channelId, start, stop, title ->
                        navController.navigate(
                            VodPlayerRoute(
                                contentId = "",
                                catchupChannelId = channelId,
                                catchupStart = start,
                                catchupStop = stop,
                                catchupTitle = title,
                            ),
                        )
                    },
                )
            }
            composable<PlayerRoute> { PlayerScreen() }
            vodDestinations(navController)
            composable<SettingsRoute> { SettingsScreen(onAddPlaylist = { navController.navigate(AddPlaylistRoute) }) }
            composable<AddPlaylistRoute> { AddPlaylistScreen(onDone = { navController.goTo(LiveRoute) }) }
        }
    }
}

/** Movies, series, their pages and the VOD player (shared by the TV and phone shells). */
internal fun androidx.navigation.NavGraphBuilder.vodDestinations(navController: NavHostController) {
    composable<MoviesRoute> {
        VodBrowseScreen(VodKind.MOVIE, onOpen = { navController.navigate(MovieRoute(it)) })
    }
    composable<SeriesListRoute> {
        VodBrowseScreen(VodKind.SERIES, onOpen = { navController.navigate(SeriesRoute(it)) })
    }
    composable<MovieRoute> {
        MovieScreen(onPlay = { id, fromStart -> navController.navigate(VodPlayerRoute(id, fromStart)) })
    }
    composable<SeriesRoute> {
        SeriesScreen(onPlay = { navController.navigate(VodPlayerRoute(it)) })
    }
    composable<VodPlayerRoute> {
        VodPlayerScreen(onExit = { navController.popBackStack() })
    }
}

/**
 * Top-level switch: one copy of each destination in the back stack, Back returns to the start.
 * The start destination (TV) is never saved or restored: restoring it would bring back the
 * screens that were above it.
 */
internal fun NavHostController.goTo(route: Any) {
    if (route == LiveRoute) {
        popBackStack(LiveRoute, inclusive = false)
        return
    }
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
