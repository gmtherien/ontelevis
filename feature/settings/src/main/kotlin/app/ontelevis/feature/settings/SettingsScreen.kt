package app.ontelevis.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.ontelevis.core.designsystem.PlaceholderScreen

@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.feature_settings_title),
        message = stringResource(R.string.feature_settings_placeholder),
        modifier = modifier,
    )
}
