package app.ontelevis.navigation

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
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import app.ontelevis.feature.guide.GuideScreen
import app.ontelevis.feature.live.LiveScreen
import app.ontelevis.feature.settings.SettingsScreen

/** Phone / tablet: bottom navigation bar. */
@Composable
fun PhoneApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val current = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val title = stringResource(destination.titleRes)
                    NavigationBarItem(
                        selected = current?.hasRoute(destination.route::class) == true,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(painterResource(destination.iconRes), contentDescription = null) },
                        label = { Text(title) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = LiveRoute,
            modifier = Modifier.padding(padding),
        ) {
            composable<LiveRoute> { LiveScreen() }
            composable<GuideRoute> { GuideScreen() }
            composable<SettingsRoute> { SettingsScreen() }
        }
    }
}
