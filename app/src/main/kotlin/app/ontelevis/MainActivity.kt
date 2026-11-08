package app.ontelevis

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.ontelevis.core.designsystem.OntelevisPhoneTheme
import app.ontelevis.core.designsystem.OntelevisTvTheme
import app.ontelevis.navigation.PhoneApp
import app.ontelevis.navigation.TvApp
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val tv = isTelevision()
        if (!tv) enableEdgeToEdge()
        setContent {
            if (tv) {
                OntelevisTvTheme { TvApp() }
            } else {
                OntelevisPhoneTheme { PhoneApp() }
            }
        }
    }
}
