package app.ontelevis.core.playerui

import android.os.SystemClock
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import app.ontelevis.core.player.LivePlayer
import app.ontelevis.core.player.VodPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Stops playback after a while (falling asleep in front of the TV). Not kept across app restarts. */
@Singleton
class SleepTimer
    @Inject
    constructor() {
        private val _endsAt = MutableStateFlow<Long?>(null)

        /** [SystemClock.elapsedRealtime] when playback stops, null when off. */
        val endsAt: StateFlow<Long?> = _endsAt.asStateFlow()

        fun set(minutes: Int?) {
            _endsAt.value = minutes?.let { SystemClock.elapsedRealtime() + it * 60_000L }
        }

        /** Whole minutes left (rounded up), null when off. */
        fun minutesLeft(): Int? =
            _endsAt.value?.let { ((it - SystemClock.elapsedRealtime() + 59_999) / 60_000).toInt() }

        companion object {
            val CHOICES = listOf(15, 30, 60, 90, 120)
        }
    }

@HiltViewModel
class SleepTimerViewModel
    @Inject
    constructor(
        val timer: SleepTimer,
        private val livePlayer: LivePlayer,
        private val vodPlayer: VodPlayer,
    ) : ViewModel() {
        fun stopPlayback() {
            timer.set(null)
            vodPlayer.stop()
            livePlayer.stop()
        }
    }

/**
 * Drawn once at the root of the app: when the timer runs out, asks "Still watching?" for a minute,
 * then stops both players and calls [onStopped] (the app goes back to its start screen).
 */
@Composable
fun SleepTimerHost(
    onStopped: () -> Unit,
    viewModel: SleepTimerViewModel = hiltViewModel(),
) {
    val endsAt by viewModel.timer.endsAt.collectAsStateWithLifecycle()
    var asking by remember { mutableStateOf(false) }
    var secondsLeft by remember { mutableIntStateOf(PROMPT_SECONDS) }
    val context = LocalContext.current
    val stopped = stringResource(R.string.sleep_stopped)
    LaunchedEffect(endsAt) {
        asking = false
        val end = endsAt ?: return@LaunchedEffect
        delay((end - SystemClock.elapsedRealtime()).coerceAtLeast(0))
        secondsLeft = PROMPT_SECONDS
        asking = true
        while (secondsLeft > 0) {
            delay(1_000)
            secondsLeft--
        }
        asking = false
        viewModel.stopPlayback()
        Toast.makeText(context, stopped, Toast.LENGTH_LONG).show()
        onStopped()
    }
    if (asking) {
        OntMenu(
            title = stringResource(R.string.sleep_prompt, secondsLeft),
            options = listOf(OntMenuOption(stringResource(R.string.sleep_continue)) { viewModel.timer.set(null) }),
            onDismiss = { viewModel.timer.set(null) },
        )
    }
}

private const val PROMPT_SECONDS = 60
