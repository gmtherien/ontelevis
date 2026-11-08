package app.ontelevis.feature.live

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.ontelevis.core.designsystem.PlaceholderScreen

@Composable
fun LiveScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.feature_live_title),
        message = stringResource(R.string.feature_live_placeholder),
        modifier = modifier,
    )
}
