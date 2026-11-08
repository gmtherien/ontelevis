package app.ontelevis.core.designsystem.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Typography
import androidx.tv.material3.darkColorScheme

@Composable
fun OntelevisTheme(
    accent: Color = OntPalette.Accent,
    content: @Composable () -> Unit,
) {
    val colors = remember(accent) { OntColors(accent = accent) }
    val type = remember { OntTypography() }

    // Mapped so stock androidx.tv.material3 components look right out of the box.
    val scheme =
        darkColorScheme(
            primary = accent,
            onPrimary = colors.onAccent,
            secondary = accent,
            onSecondary = colors.onAccent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.surface1,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.textMuted,
            inverseSurface = colors.focusSurface,
            inverseOnSurface = colors.onFocus,
            border = colors.lineStrong,
            borderVariant = colors.line,
            error = colors.live,
        )

    val tvType =
        Typography(
            displaySmall = type.display,
            headlineSmall = type.title,
            titleMedium = type.itemStrong,
            titleSmall = type.item,
            bodyMedium = type.body,
            bodySmall = type.meta,
            labelLarge = type.button,
            labelMedium = type.tag,
            labelSmall = type.sectionLabel,
        )

    CompositionLocalProvider(
        LocalOntColors provides colors,
        LocalOntTypography provides type,
    ) {
        MaterialTheme(colorScheme = scheme, typography = tvType, content = content)
    }
}

/** Access tokens anywhere: `OntTheme.colors.accent`, `OntTheme.type.meta`. */
object OntTheme {
    val colors: OntColors
        @Composable @ReadOnlyComposable
        get() = LocalOntColors.current
    val type: OntTypography
        @Composable @ReadOnlyComposable
        get() = LocalOntTypography.current
}
