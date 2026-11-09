package app.ontelevis.core.data

import android.content.Context
import androidx.core.content.edit
import app.ontelevis.core.model.PictureFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Player defaults kept on the device (Settings › Playback); each channel or title can override them. */
@Singleton
class PlayerSettings
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) {
        private val prefs = context.getSharedPreferences("player", Context.MODE_PRIVATE)
        private val _audioLanguage = MutableStateFlow(prefs.getString(KEY_AUDIO, null))
        private val _subtitleLanguage = MutableStateFlow(prefs.getString(KEY_TEXT, null))
        private val _format = MutableStateFlow(formatOf(prefs.getString(KEY_FORMAT, null)))
        private val _showStats = MutableStateFlow(prefs.getBoolean(KEY_STATS, false))
        private val _timeshift = MutableStateFlow(prefs.getBoolean(KEY_TIMESHIFT, false))

        /** Live pause: streams go through a file on disk so Pause works (uses storage while watching). */
        val timeshift: StateFlow<Boolean> = _timeshift.asStateFlow()

        fun setTimeshift(value: Boolean) {
            prefs.edit { putBoolean(KEY_TIMESHIFT, value) }
            _timeshift.value = value
        }

        /** Preferred audio language (ISO 639 code like "fr"), null for the stream's own default track. */
        val audioLanguage: StateFlow<String?> = _audioLanguage.asStateFlow()

        /** Subtitle language shown by default, null for no subtitles. */
        val subtitleLanguage: StateFlow<String?> = _subtitleLanguage.asStateFlow()

        val format: StateFlow<PictureFormat> = _format.asStateFlow()

        /** Resolution, codecs and bitrate over the video. */
        val showStats: StateFlow<Boolean> = _showStats.asStateFlow()

        fun setAudioLanguage(value: String?) {
            prefs.edit { putString(KEY_AUDIO, value) }
            _audioLanguage.value = value
        }

        fun setSubtitleLanguage(value: String?) {
            prefs.edit { putString(KEY_TEXT, value) }
            _subtitleLanguage.value = value
        }

        fun setFormat(value: PictureFormat) {
            prefs.edit { putString(KEY_FORMAT, value.name) }
            _format.value = value
        }

        fun setShowStats(value: Boolean) {
            prefs.edit { putBoolean(KEY_STATS, value) }
            _showStats.value = value
        }

        /** For the backup. */
        fun all(): Map<String, String> =
            buildMap {
                _audioLanguage.value?.let { put(KEY_AUDIO, it) }
                _subtitleLanguage.value?.let { put(KEY_TEXT, it) }
                put(KEY_FORMAT, _format.value.name)
            }

        fun restore(values: Map<String, String>) {
            setAudioLanguage(values[KEY_AUDIO])
            setSubtitleLanguage(values[KEY_TEXT])
            setFormat(formatOf(values[KEY_FORMAT]))
        }

        private fun formatOf(name: String?) = PictureFormat.entries.firstOrNull { it.name == name } ?: PictureFormat.FIT

        private companion object {
            const val KEY_AUDIO = "audio_language"
            const val KEY_TEXT = "subtitle_language"
            const val KEY_FORMAT = "format"
            const val KEY_STATS = "show_stats"
            const val KEY_TIMESHIFT = "timeshift"
        }
    }
