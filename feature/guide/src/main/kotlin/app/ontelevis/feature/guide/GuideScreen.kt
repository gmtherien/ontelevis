package app.ontelevis.feature.guide

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.ontelevis.core.designsystem.PlaceholderScreen

@Composable
fun GuideScreen(modifier: Modifier = Modifier) {
    PlaceholderScreen(
        title = stringResource(R.string.feature_guide_title),
        message = stringResource(R.string.feature_guide_placeholder),
        modifier = modifier,
    )
}
