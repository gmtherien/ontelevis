package app.ontelevis.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ontelevis.feature.live.LiveScreen
import app.ontelevis.feature.live.PlayerScreen
import app.ontelevis.feature.settings.AddPlaylistScreen
import app.ontelevis.feature.settings.SettingsScreen
import app.ontelevis.feature.settings.UpdatePrompt
import app.ontelevis.feature.vod.MoviesRoute
import app.ontelevis.feature.vod.SeriesListRoute
import app.ontelevis.feature.vod.VodPlayerRoute
import app.ontelevis.core.designsystem.R as DesignR
import app.ontelevis.feature.live.R as LiveR
import app.ontelevis.feature.settings.R as SettingsR
import app.ontelevis.feature.vod.R as VodR

private enum class PhoneTab(
    val route: Any,
    @param:StringRes val title: Int,
    @param:DrawableRes val icon: Int,
) {
    TV(LiveRoute, LiveR.string.feature_live_title, DesignR.drawable.ont_ic_television),
    MOVIES(MoviesRoute, VodR.string.vod_movies, DesignR.drawable.ont_ic_film_strip),
    SERIES(SeriesListRoute, VodR.string.vod_series, DesignR.drawable.ont_ic_monitor_play),
    SETTINGS(SettingsRoute, SettingsR.string.feature_settings_title, DesignR.drawable.ont_ic_sliders_horizontal),
}

/** Phone / tablet: bottom navigation bar. */
@Composable
fun PhoneApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            if (current?.hasRoute(PlayerRoute::class) != true && current?.hasRoute(VodPlayerRoute::class) != true) {
                NavigationBar {
                    PhoneTab.entries.forEach { tab ->
                        val selected =
                            current?.hasRoute(tab.route::class) == true ||
                                (tab == PhoneTab.SETTINGS && current?.hasRoute(AddPlaylistRoute::class) == true)
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.goTo(tab.route) },
                            icon = { Icon(painterResource(tab.icon), contentDescription = null) },
                            label = { Text(stringResource(tab.title)) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = LiveRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<LiveRoute> {
                LiveScreen(
                    onAddPlaylist = { navController.navigate(AddPlaylistRoute) },
                    onOpenFullscreen = { navController.navigate(PlayerRoute) },
                )
            }
            composable<PlayerRoute> { PlayerScreen() }
            vodDestinations(navController)
            composable<SettingsRoute> { SettingsScreen(onAddPlaylist = { navController.navigate(AddPlaylistRoute) }) }
            composable<AddPlaylistRoute> { AddPlaylistScreen(onDone = { navController.goTo(LiveRoute) }) }
        }
    }
    // New version on GitHub: asked at each start, never over a full-screen player.
    UpdatePrompt(
        allowed =
            current != null && current.hasRoute(PlayerRoute::class).not() &&
                current.hasRoute(VodPlayerRoute::class).not(),
    )
}
