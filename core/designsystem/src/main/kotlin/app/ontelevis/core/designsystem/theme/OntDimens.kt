package app.ontelevis.core.designsystem.theme

import androidx.compose.ui.unit.dp

/**
 * Android TV renders 1920×1080 as 960×540 dp. Overscan-safe margins: 48 dp sides, 27 dp top/bottom.
 * Mockups are drawn at 1920×1080 px: divide every px value by 2.
 */
object OntDimens {
    // Safe area
    val SafeHorizontal = 48.dp
    val SafeVertical = 27.dp

    // Columns
    val NavRailWidth = 72.dp // collapsed; expanded drawer: 240.dp
    val NavRailExpandedWidth = 240.dp
    val CategoryPanelWidth = 208.dp
    val ContentStartPadding = 24.dp // between category panel and content
    val ContentEndPadding = SafeHorizontal

    // Nav rail
    val NavItemWidth = 48.dp
    val NavItemHeight = 44.dp
    val NavIcon = 24.dp
    val LogoSize = 40.dp

    // Lists
    val ListItemHeight = 36.dp
    val ListItemPaddingH = 10.dp
    val SelectionDot = 6.dp

    // Now playing
    val NowPlayingHeight = 190.dp // player is 16:9 => 338×190 dp
    val PlayerWidth = 338.dp
    val InfoLogoTile = 48.dp

    // EPG
    val EpgChannelColumn = 220.dp
    val EpgHeaderHeight = 36.dp
    val EpgRowHeight = 51.dp
    val EpgBlockInsetV = 6.dp
    val EpgBlockInsetH = 8.dp
    val EpgChannelLogo = 34.dp
    val NowLine = 2.dp
    val ProgressBar = 3.dp

    // Buttons
    val ButtonHeight = 40.dp
    val IconButton = 40.dp

    // Radii
    val RadiusTag = 5.dp
    val RadiusItem = 8.dp
    val RadiusButton = 10.dp
    val RadiusBlock = 10.dp
    val RadiusTile = 9.dp
    val RadiusCard = 14.dp
    val RadiusNavItem = 12.dp

    // Strokes
    val Hairline = 1.dp
}

object OntMotion {
    const val FOCUS_SCALE_LIST = 1.04f
    const val FOCUS_SCALE_BUTTON = 1.06f
    const val FOCUS_SCALE_BLOCK = 1.02f
    const val FOCUS_ANIM_MS = 150
    const val CONTROLS_HIDE_DELAY_MS = 3_000L
}
