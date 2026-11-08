package app.ontelevis.core.designsystem

import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.MaterialTheme as PhoneMaterialTheme
import androidx.tv.material3.LocalContentColor as TvLocalContentColor
import androidx.tv.material3.MaterialTheme as TvMaterialTheme
import androidx.tv.material3.darkColorScheme as tvDarkColorScheme

/** Brand colours. Placeholder palette, to be refined with the logo. */
object OntelevisColors {
    val Background = Color(0xFF0E1116)
    val Surface = Color(0xFF181C23)
    val Primary = Color(0xFF3DD6C6)
    val OnPrimary = Color(0xFF00201D)
    val OnBackground = Color(0xFFE6E8EC)
    val Muted = Color(0xFF9AA1AD)
}

@Composable
fun OntelevisPhoneTheme(content: @Composable () -> Unit) {
    PhoneMaterialTheme(
        colorScheme =
            darkColorScheme(
                primary = OntelevisColors.Primary,
                onPrimary = OntelevisColors.OnPrimary,
                background = OntelevisColors.Background,
                onBackground = OntelevisColors.OnBackground,
                surface = OntelevisColors.Surface,
                onSurface = OntelevisColors.OnBackground,
            ),
        content = content,
    )
}

@Composable
fun OntelevisTvTheme(content: @Composable () -> Unit) {
    TvMaterialTheme(
        colorScheme =
            tvDarkColorScheme(
                primary = OntelevisColors.Primary,
                onPrimary = OntelevisColors.OnPrimary,
                background = OntelevisColors.Background,
                onBackground = OntelevisColors.OnBackground,
                surface = OntelevisColors.Surface,
                onSurface = OntelevisColors.OnBackground,
            ),
    ) {
        // TV screens draw on the dark background directly (no Surface), so text needs a default colour.
        CompositionLocalProvider(TvLocalContentColor provides OntelevisColors.OnBackground, content = content)
    }
}
