package app.ontelevis

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import app.ontelevis.core.data.LanguageSettings
import app.ontelevis.core.data.ParentalControl
import app.ontelevis.core.data.sync.PlaylistSync
import app.ontelevis.core.designsystem.component.LocalIsTv
import app.ontelevis.core.designsystem.theme.OntelevisPhoneTheme
import app.ontelevis.core.designsystem.theme.OntelevisTheme
import app.ontelevis.core.player.LivePlayer
import app.ontelevis.core.player.VodPlayer
import app.ontelevis.navigation.PhoneApp
import app.ontelevis.navigation.TvApp
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        // Language chosen in Settings, on devices older than Android 13.
        super.attachBaseContext(LanguageSettings.wrap(newBase))
    }

    // Shared live player: released while the app is in the background, resumed when it returns.
    @Inject lateinit var livePlayer: LivePlayer

    @Inject lateinit var vodPlayer: VodPlayer

    @Inject lateinit var parental: ParentalControl

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PlaylistSync.schedule(applicationContext)
        val tv = isTelevision()
        if (!tv) enableEdgeToEdge()
        setContent {
            CompositionLocalProvider(LocalIsTv provides tv) {
                if (tv) {
                    OntelevisTheme { TvApp() }
                } else {
                    OntelevisPhoneTheme { PhoneApp() }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        livePlayer.resume()
    }

    override fun onStop() {
        // A movie pauses (it does not reconnect on its own like a live stream).
        vodPlayer.pause()
        livePlayer.suspend()
        // Locked categories ask for the PIN again when the app comes back.
        parental.relock()
        super.onStop()
    }
}
