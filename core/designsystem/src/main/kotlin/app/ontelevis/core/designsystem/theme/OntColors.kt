package app.ontelevis.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Raw palette. Source of truth: design/DESIGN_HANDOFF.md §2. */
object OntPalette {
    val Background = Color(0xFF0A0B0E) // app background, nav rail
    val GuideBackground = Color(0xFF0D0E12) // EPG container
    val Surface1 = Color(0xFF12141A) // secondary buttons
    val Surface2 = Color(0xFF171A21) // selected list item, logo tiles
    val Surface3 = Color(0xFF1A1D24) // current program block
    val ProgramUpcoming = Color(0xFF14161C)
    val ProgramPast = Color(0xFF0F1014)
    val SelectedRow = Color(0xFF111318) // row of the channel being played

    val Line = Color(0xFF1A1D24) // section dividers
    val LineSubtle = Color(0xFF16181E) // EPG row dividers
    val LineStrong = Color(0xFF2A2E37) // tag outlines
    val TileBorder = Color(0xFF23272F)
    val ButtonBorder = Color(0xFF262A33)
    val NavDivider = Color(0xFF1F232B)

    val TextPrimary = Color(0xFFECEDEF)
    val TextSecondary = Color(0xFFC4C8D0)
    val TextMuted = Color(0xFFA2A7B3)
    val TextTertiary = Color(0xFF7D8390) // lowest allowed contrast for text
    val TextNav = Color(0xFF8B909C) // inactive rail icons

    val FocusSurface = Color(0xFFECEDEF) // focused element background
    val OnFocus = Color(0xFF0A0B0E)
    val OnFocusMuted = Color(0xFF3A3F4A) // secondary text on a focused element

    val Accent = Color(0xFFF2A93B) // Ontelevis amber: selection, now-line, primary CTA
    val AccentOnFocus = Color(0xFFB8741A) // selection dot when the item is also focused
    val OnAccent = Color(0xFF1A1206)
    val Live = Color(0xFFFF5A4E) // "EN DIRECT" dot (on dark)
    val LiveOnFocus = Color(0xFFE5372B) // live dot on a focused (light) element

    // Owner, 2026-11-08: a red dot reads as "recording". Playing / live is shown in green; red stays
    // for errors (and a future recording indicator).
    val Playing = Color(0xFF3DD68C) // "live" / "now playing" dot on dark
    val PlayingOnFocus = Color(0xFF178A4F) // same dot on a focused (light) element
}

@Immutable
data class OntColors(
    val background: Color = OntPalette.Background,
    val guideBackground: Color = OntPalette.GuideBackground,
    val surface1: Color = OntPalette.Surface1,
    val surface2: Color = OntPalette.Surface2,
    val surface3: Color = OntPalette.Surface3,
    val programUpcoming: Color = OntPalette.ProgramUpcoming,
    val programPast: Color = OntPalette.ProgramPast,
    val selectedRow: Color = OntPalette.SelectedRow,
    val line: Color = OntPalette.Line,
    val lineSubtle: Color = OntPalette.LineSubtle,
    val lineStrong: Color = OntPalette.LineStrong,
    val tileBorder: Color = OntPalette.TileBorder,
    val buttonBorder: Color = OntPalette.ButtonBorder,
    val navDivider: Color = OntPalette.NavDivider,
    val textPrimary: Color = OntPalette.TextPrimary,
    val textSecondary: Color = OntPalette.TextSecondary,
    val textMuted: Color = OntPalette.TextMuted,
    val textTertiary: Color = OntPalette.TextTertiary,
    val textNav: Color = OntPalette.TextNav,
    val focusSurface: Color = OntPalette.FocusSurface,
    val onFocus: Color = OntPalette.OnFocus,
    val onFocusMuted: Color = OntPalette.OnFocusMuted,
    val accent: Color = OntPalette.Accent,
    val accentOnFocus: Color = OntPalette.AccentOnFocus,
    val onAccent: Color = OntPalette.OnAccent,
    val live: Color = OntPalette.Live,
    val liveOnFocus: Color = OntPalette.LiveOnFocus,
    val playing: Color = OntPalette.Playing,
    val playingOnFocus: Color = OntPalette.PlayingOnFocus,
)

val LocalOntColors = staticCompositionLocalOf { OntColors() }
