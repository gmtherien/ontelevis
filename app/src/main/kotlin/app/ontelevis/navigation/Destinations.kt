package app.ontelevis.navigation

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.ontelevis.R
import kotlinx.serialization.Serializable
import app.ontelevis.feature.guide.R as GuideR
import app.ontelevis.feature.live.R as LiveR
import app.ontelevis.feature.settings.R as SettingsR

@Serializable data object HomeRoute

@Serializable data object LiveRoute

@Serializable data object GuideRoute

@Serializable data object SettingsRoute

enum class TopLevelDestination(
    val route: Any,
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
) {
    LIVE(LiveRoute, LiveR.string.feature_live_title, R.drawable.ic_nav_live),
    GUIDE(GuideRoute, GuideR.string.feature_guide_title, R.drawable.ic_nav_guide),
    SETTINGS(SettingsRoute, SettingsR.string.feature_settings_title, R.drawable.ic_nav_settings),
}
