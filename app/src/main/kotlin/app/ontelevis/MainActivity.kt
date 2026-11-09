package app.ontelevis

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.lifecycleScope
import app.ontelevis.core.data.LanguageSettings
import app.ontelevis.core.data.LaunchRequests
import app.ontelevis.core.data.ParentalControl
import app.ontelevis.core.data.WatchNextPublisher
import app.ontelevis.core.data.reminder.ReminderCenter
import app.ontelevis.core.data.reminder.ReminderRepository
import app.ontelevis.core.data.sync.PlaylistSync
import app.ontelevis.core.designsystem.component.LocalIsTv
import app.ontelevis.core.designsystem.theme.OntelevisPhoneTheme
import app.ontelevis.core.designsystem.theme.OntelevisTheme
import app.ontelevis.core.player.LivePlayer
import app.ontelevis.core.player.VodPlayer
import app.ontelevis.core.playerui.PlaybackOptions
import app.ontelevis.navigation.PhoneApp
import app.ontelevis.navigation.TvApp
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

    // Tracks and picture format remembered per channel / title, applied as each one starts.
    @Inject lateinit var playbackOptions: PlaybackOptions

    @Inject lateinit var launchRequests: LaunchRequests

    @Inject lateinit var reminderCenter: ReminderCenter

    @Inject lateinit var reminders: ReminderRepository

    @Inject lateinit var watchNext: WatchNextPublisher

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        playbackOptions.start()
        // Google TV's "Continue watching" row follows the resume points.
        watchNext.start()
        // Started from a reminder, Google TV or a voice search (not when the activity is recreated).
        if (savedInstanceState == null) LaunchRequests.parse(intent)?.let(launchRequests::post)
        // Off the main thread: WorkManager's first use opens its database.
        lifecycleScope.launch(Dispatchers.IO) {
            PlaylistSync.schedule(applicationContext)
            // Alarms are lost when the app is updated or force-stopped: set them again.
            reminders.scheduleAll()
        }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        LaunchRequests.parse(intent)?.let(launchRequests::post)
    }

    override fun onStart() {
        super.onStart()
        reminderCenter.foreground = true
        livePlayer.resume()
    }

    override fun onStop() {
        reminderCenter.foreground = false
        // A movie pauses (it does not reconnect on its own like a live stream).
        vodPlayer.pause()
        livePlayer.suspend()
        // Locked categories ask for the PIN again when the app comes back.
        parental.relock()
        super.onStop()
    }
}
