package app.ontelevis.feature.settings

import app.ontelevis.core.data.AppUpdater
import app.ontelevis.core.data.AvailableUpdate
import app.ontelevis.core.data.UpdateError
import app.ontelevis.core.data.UpdateException
import app.ontelevis.core.data.UpdatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What the update dialog shows. */
sealed interface UpdateState {
    data object Idle : UpdateState

    data object Checking : UpdateState

    data class Available(
        val update: AvailableUpdate,
    ) : UpdateState

    /** [progress] 0..1, or -1 when the size is unknown. */
    data class Downloading(
        val update: AvailableUpdate,
        val progress: Float,
    ) : UpdateState

    /** The app may not install apps yet: the system screen was opened; retry when back. */
    data class NeedsPermission(
        val file: File,
    ) : UpdateState

    data object UpToDate : UpdateState

    data class Failed(
        val error: UpdateError,
    ) : UpdateState
}

/**
 * One update flow for the whole app: the startup check and Settings' "Check for updates" share it,
 * and the dialog ([UpdatePrompt]) is drawn once at the root of the app.
 */
@Singleton
class UpdateCenter
    @Inject
    constructor(
        private val updater: AppUpdater,
        private val prefs: UpdatePrefs,
    ) {
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
        val state: StateFlow<UpdateState> = _state.asStateFlow()
        private var startupDone = false
        private var job: Job? = null

        val versionName: String get() = updater.installedVersionName
        val canUpdate: Boolean get() = updater.canUpdate
        val betas: StateFlow<Boolean> = prefs.betas

        fun setBetas(value: Boolean) = prefs.setBetas(value)

        /** Once per app start: offer a newer version, stay silent otherwise (no network, up to date). */
        fun startupCheck() {
            if (startupDone || !updater.canUpdate) return
            startupDone = true
            job =
                scope.launch {
                    updater.cleanUp()
                    val update = runCatching { updater.check() }.getOrNull()
                    if (update != null && _state.value == UpdateState.Idle) _state.value = UpdateState.Available(update)
                }
        }

        /** From Settings: says the result, including "up to date" and errors. */
        fun manualCheck() {
            if (!updater.canUpdate) return
            job?.cancel()
            _state.value = UpdateState.Checking
            job =
                scope.launch {
                    _state.value =
                        try {
                            updater.check()?.let { UpdateState.Available(it) } ?: UpdateState.UpToDate
                        } catch (e: UpdateException) {
                            UpdateState.Failed(e.error)
                        }
                }
        }

        fun update(update: AvailableUpdate) {
            job?.cancel()
            _state.value = UpdateState.Downloading(update, 0f)
            job =
                scope.launch {
                    try {
                        val file =
                            updater.download(update) { p ->
                                scope.launch {
                                    (_state.value as? UpdateState.Downloading)?.let {
                                        _state.value =
                                            it.copy(progress = p)
                                    }
                                }
                            }
                        install(file)
                    } catch (e: UpdateException) {
                        _state.value = UpdateState.Failed(e.error)
                    }
                }
        }

        fun install(file: File) {
            _state.value = if (updater.install(file)) UpdateState.Idle else UpdateState.NeedsPermission(file)
        }

        fun dismiss() {
            job?.cancel()
            _state.value = UpdateState.Idle
        }
    }
