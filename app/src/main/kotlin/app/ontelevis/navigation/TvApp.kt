package app.ontelevis.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.ontelevis.R
import app.ontelevis.core.designsystem.OntelevisColors
import app.ontelevis.feature.guide.GuideScreen
import app.ontelevis.feature.live.LiveScreen
import app.ontelevis.feature.settings.SettingsScreen

/** TV / D-pad navigation. Back pops to the home row (system Back handled by NavHost). */
@Composable
fun TvApp() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = HomeRoute) {
        composable<HomeRoute> { TvHome(onOpen = { navController.navigate(it.route) }) }
        composable<LiveRoute> { LiveScreen() }
        composable<GuideRoute> { GuideScreen() }
        composable<SettingsRoute> { SettingsScreen() }
    }
}

@Composable
private fun TvHome(onOpen: (TopLevelDestination) -> Unit) {
    val firstTile = remember { FocusRequester() }
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(OntelevisColors.Background)
                // TV overscan-safe margins.
                .padding(horizontal = 58.dp, vertical = 40.dp),
    ) {
        Text(text = stringResource(R.string.app_name), style = MaterialTheme.typography.displaySmall)
        Text(
            text = stringResource(R.string.home_tagline),
            style = MaterialTheme.typography.bodyLarge,
            color = OntelevisColors.Muted,
        )
        Spacer(Modifier.height(48.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            TopLevelDestination.entries.forEachIndexed { index, destination ->
                Card(
                    onClick = { onOpen(destination) },
                    modifier =
                        Modifier
                            .size(width = 240.dp, height = 135.dp)
                            .then(if (index == 0) Modifier.focusRequester(firstTile) else Modifier),
                ) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(text = stringResource(destination.titleRes), style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
        }
    }
    // Something must hold focus at all times, or the remote looks frozen.
    LaunchedEffect(Unit) { firstTile.requestFocus() }
}
