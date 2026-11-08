package app.ontelevis.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.ontelevis.core.designsystem.theme.OntTheme

/** Temporary screen for features not built yet. Works under both the TV and phone themes. */
@Composable
fun PlaceholderScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
) {
    val colors = OntTheme.colors
    Column(
        modifier = modifier.fillMaxSize().background(colors.background).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BasicText(text = title, style = OntTheme.type.title.copy(color = colors.textPrimary))
        BasicText(
            text = message,
            style = OntTheme.type.body.copy(color = colors.textMuted, textAlign = TextAlign.Center),
        )
    }
}
