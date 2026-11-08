package app.ontelevis

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** True on Android TV, Google TV and Fire TV: the D-pad UI is used there. */
fun Context.isTelevision(): Boolean {
    val uiMode = getSystemService(UiModeManager::class.java)?.currentModeType
    return uiMode == Configuration.UI_MODE_TYPE_TELEVISION ||
        packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        packageManager.hasSystemFeature(FIRE_TV_FEATURE)
}

private const val FIRE_TV_FEATURE = "amazon.hardware.fire_tv"
