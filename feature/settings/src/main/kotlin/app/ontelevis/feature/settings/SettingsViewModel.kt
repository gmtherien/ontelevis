package app.ontelevis.feature.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ontelevis.core.data.BackupFile
import app.ontelevis.core.data.BackupFiles
import app.ontelevis.core.data.BackupManager
import app.ontelevis.core.data.BackupSummary
import app.ontelevis.core.data.LanguageSettings
import app.ontelevis.core.data.ParentalControl
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.data.sync.PlaylistSync
import app.ontelevis.core.model.Playlist
import app.ontelevis.core.model.PlaylistSource
import app.ontelevis.core.model.RefreshError
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URI
import javax.inject.Inject

/** What happened to the last backup action. */
sealed interface BackupMessage {
    /** Saved; [folder] when it went to Downloads (no file picker). */
    data class Saved(
        val folder: String?,
    ) : BackupMessage

    data class Restored(
        val recreate: Boolean,
    ) : BackupMessage

    data object Unreadable : BackupMessage

    data object Failed : BackupMessage
}

@HiltViewModel
class SettingsViewModel
    @Inject
    constructor(
        private val repository: PlaylistRepository,
        private val languageSettings: LanguageSettings,
        private val parental: ParentalControl,
        private val backups: BackupManager,
        val backupFiles: BackupFiles,
    ) : ViewModel() {
        /** A backup read and waiting for the user's confirmation. */
        data class PendingRestore(
            val name: String,
            val text: String,
            val summary: BackupSummary,
        )

        private val _pendingRestore = MutableStateFlow<PendingRestore?>(null)
        val pendingRestore: StateFlow<PendingRestore?> = _pendingRestore.asStateFlow()

        /** Backups found in Downloads/Ontelevis (TV boxes without a file picker), null when not listed. */
        private val _downloadBackups = MutableStateFlow<List<BackupFile>?>(null)
        val downloadBackups: StateFlow<List<BackupFile>?> = _downloadBackups.asStateFlow()

        /** One-off message for the screen (saved, restored, unreadable file...). */
        private val _backupMessage = MutableStateFlow<BackupMessage?>(null)
        val backupMessage: StateFlow<BackupMessage?> = _backupMessage.asStateFlow()

        fun consumeBackupMessage() {
            _backupMessage.value = null
        }

        fun saveTo(uri: Uri) {
            viewModelScope.launch {
                _backupMessage.value =
                    runCatching { backupFiles.write(uri, backups.export()) }
                        .fold({ BackupMessage.Saved(null) }, { BackupMessage.Failed })
            }
        }

        fun saveToDownloads() {
            viewModelScope.launch {
                _backupMessage.value =
                    runCatching { backupFiles.saveToDownloads(backupFiles.suggestedName(), backups.export()) }
                        .fold({ BackupMessage.Saved(it) }, { BackupMessage.Failed })
            }
        }

        fun listDownloads() {
            viewModelScope.launch {
                _downloadBackups.value = runCatching { backupFiles.listDownloads() }.getOrDefault(emptyList())
            }
        }

        fun closeDownloads() {
            _downloadBackups.value = null
        }

        /** Reads a backup and asks for confirmation (nothing changes yet). */
        fun open(
            uri: Uri,
            name: String,
        ) {
            _downloadBackups.value = null
            viewModelScope.launch {
                val text = runCatching { backupFiles.read(uri) }.getOrNull()
                val summary = text?.let { runCatching { backups.summary(it) }.getOrNull() }
                if (text == null || summary == null) {
                    _backupMessage.value = BackupMessage.Unreadable
                } else {
                    _pendingRestore.value = PendingRestore(name, text, summary)
                }
            }
        }

        fun cancelRestore() {
            _pendingRestore.value = null
        }

        /** Replaces the setup by the pending backup. */
        fun confirmRestore() {
            val pending = _pendingRestore.value ?: return
            _pendingRestore.value = null
            viewModelScope.launch {
                _backupMessage.value =
                    runCatching { backups.restore(pending.text) }
                        .fold({ recreate ->
                            _language.value = languageSettings.current()
                            BackupMessage.Restored(recreate)
                        }, { BackupMessage.Unreadable })
            }
        }

        val hasPin: StateFlow<Boolean> = parental.hasPin

        fun checkPin(pin: String): Boolean = parental.checkPin(pin)

        fun setPin(pin: String) = parental.setPin(pin)

        fun clearPin() = parental.clearPin()

        /** Null while loading. */
        val playlists: StateFlow<List<Playlist>?> =
            repository.observePlaylists().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        private val _refreshing = MutableStateFlow<Set<Long>>(emptySet())
        val refreshing: StateFlow<Set<Long>> = _refreshing.asStateFlow()

        fun refresh(id: Long) {
            if (id in _refreshing.value) return
            _refreshing.update { it + id }
            viewModelScope.launch {
                try {
                    if (repository.refresh(id) == null) {
                        repository.refreshVod(id)
                        repository.refreshEpg(id)
                    }
                } finally {
                    _refreshing.update { it - id }
                }
            }
        }

        fun delete(id: Long) {
            viewModelScope.launch { repository.remove(id) }
        }

        /** "" = system language. */
        private val _language = MutableStateFlow(languageSettings.current())
        val language: StateFlow<String> = _language.asStateFlow()
        val languages: List<String> get() = languageSettings.supported

        /** Returns true when the screen must recreate the activity to apply it (before Android 13). */
        fun setLanguage(tag: String): Boolean {
            if (tag == _language.value) return false
            _language.value = tag
            return languageSettings.set(tag)
        }
    }

enum class PlaylistKind { M3U, XTREAM }

enum class FieldError { REQUIRED, INVALID_URL }

data class AddPlaylistForm(
    val kind: PlaylistKind = PlaylistKind.M3U,
    val name: String = "",
    val url: String = "",
    /** Optional XMLTV link for an M3U playlist (else the one its header announces). */
    val epgUrl: String = "",
    val server: String = "",
    val username: String = "",
    val password: String = "",
    val errors: Map<String, FieldError> = emptyMap(),
    val saving: Boolean = false,
    /** Why the playlist could not be loaded; the form stays filled so it can be fixed. */
    val failure: RefreshError? = null,
    val done: Boolean = false,
)

@HiltViewModel
class AddPlaylistViewModel
    @Inject
    constructor(
        private val repository: PlaylistRepository,
        @param:ApplicationContext private val context: Context,
    ) : ViewModel() {
        private val _form = MutableStateFlow(AddPlaylistForm())
        val form: StateFlow<AddPlaylistForm> = _form.asStateFlow()

        fun update(change: AddPlaylistForm.() -> AddPlaylistForm) {
            _form.update { it.change().copy(errors = emptyMap(), failure = null) }
        }

        fun submit() {
            val form = _form.value
            if (form.saving || form.done) return
            val errors = validate(form)
            if (errors.isNotEmpty()) {
                _form.update { it.copy(errors = errors) }
                return
            }
            val source =
                when (form.kind) {
                    PlaylistKind.M3U -> {
                        PlaylistSource.M3u(form.url.trim(), form.epgUrl.trim().ifEmpty { null })
                    }

                    PlaylistKind.XTREAM -> {
                        PlaylistSource.Xtream(
                            form.server.trim(),
                            form.username.trim(),
                            form.password,
                        )
                    }
                }
            val name = form.name.trim().ifEmpty { defaultName(form) }
            _form.update { it.copy(saving = true, failure = null) }
            viewModelScope.launch {
                val id = repository.add(name, source)
                val error = repository.refresh(id)
                if (error == null) {
                    // The guide follows in the background: channels show right away.
                    PlaylistSync.refreshEpg(context, id)
                    _form.update { it.copy(saving = false, done = true) }
                } else {
                    // Nothing usable was loaded: drop it so the user can fix the form and retry.
                    repository.remove(id)
                    _form.update { it.copy(saving = false, failure = error) }
                }
            }
        }

        private fun validate(form: AddPlaylistForm): Map<String, FieldError> =
            buildMap {
                when (form.kind) {
                    PlaylistKind.M3U -> {
                        if (form.url.isBlank()) {
                            put(FIELD_URL, FieldError.REQUIRED)
                        } else if (!form.url.trim().isHttpUrl()) {
                            put(FIELD_URL, FieldError.INVALID_URL)
                        }
                        if (form.epgUrl.isNotBlank() &&
                            !form.epgUrl.trim().isHttpUrl()
                        ) {
                            put(FIELD_EPG, FieldError.INVALID_URL)
                        }
                    }

                    PlaylistKind.XTREAM -> {
                        if (form.server.isBlank()) put(FIELD_SERVER, FieldError.REQUIRED)
                        if (form.username.isBlank()) put(FIELD_USERNAME, FieldError.REQUIRED)
                        if (form.password.isEmpty()) put(FIELD_PASSWORD, FieldError.REQUIRED)
                    }
                }
            }

        private fun defaultName(form: AddPlaylistForm): String {
            val address = if (form.kind == PlaylistKind.M3U) form.url else form.server
            val withScheme = if (address.contains("://")) address.trim() else "http://${address.trim()}"
            return runCatching { URI(withScheme).host }.getOrNull()?.removePrefix("www.") ?: address.trim()
        }

        companion object {
            const val FIELD_URL = "url"
            const val FIELD_EPG = "epg"
            const val FIELD_SERVER = "server"
            const val FIELD_USERNAME = "username"
            const val FIELD_PASSWORD = "password"
        }
    }

private fun String.isHttpUrl(): Boolean = (startsWith("http://", true) || startsWith("https://", true)) && length > 10
