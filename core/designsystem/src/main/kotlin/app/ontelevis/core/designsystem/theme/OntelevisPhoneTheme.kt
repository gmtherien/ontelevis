package app.ontelevis.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

/**
 * Phones and tablets. The design handoff covers TV only: phones reuse the same palette,
 * fonts and tokens on Material 3 components, without the TV focus treatment.
 */
@Composable
fun OntelevisPhoneTheme(content: @Composable () -> Unit) {
    val colors = remember { OntColors() }
    val type = remember { OntTypography() }
    val scheme =
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.onAccent,
            secondary = colors.accent,
            onSecondary = colors.onAccent,
            secondaryContainer = colors.accent.copy(alpha = 0.12f),
            onSecondaryContainer = colors.accent,
            background = colors.background,
            onBackground = colors.textPrimary,
            surface = colors.background,
            onSurface = colors.textPrimary,
            surfaceVariant = colors.surface2,
            onSurfaceVariant = colors.textMuted,
            surfaceContainer = colors.surface1,
            outline = colors.lineStrong,
            outlineVariant = colors.line,
            error = colors.live,
        )
    val typography =
        Typography(
            headlineSmall = type.display,
            titleLarge = type.title,
            titleMedium = type.itemStrong,
            bodyLarge = type.item,
            bodyMedium = type.body,
            bodySmall = type.meta,
            labelLarge = type.button,
            labelMedium = type.tag,
            labelSmall = type.sectionLabel,
        )
    CompositionLocalProvider(LocalOntColors provides colors, LocalOntTypography provides type) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
