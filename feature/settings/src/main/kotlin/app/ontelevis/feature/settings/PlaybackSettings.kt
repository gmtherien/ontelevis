package app.ontelevis.feature.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.ontelevis.core.data.PlayerSettings
import app.ontelevis.core.designsystem.component.OntListItem
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import app.ontelevis.core.designsystem.component.SectionLabel
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.PictureFormat
import app.ontelevis.core.playerui.label
import java.util.Locale

private enum class PlaybackChoice { AUDIO, SUBTITLES, FORMAT }

/** Languages offered for audio and subtitles (others are still picked per channel in the player). */
private val LANGUAGES = listOf("fr", "en", "es", "it", "de", "pt", "ar", "el")

/** Settings › Playback: default audio and subtitle languages and picture format. */
@Composable
internal fun PlaybackSettingsSection(settings: PlayerSettings) {
    val audio by settings.audioLanguage.collectAsStateWithLifecycle()
    val subtitles by settings.subtitleLanguage.collectAsStateWithLifecycle()
    val format by settings.format.collectAsStateWithLifecycle()
    val timeshift by settings.timeshift.collectAsStateWithLifecycle()
    var choosing by remember { mutableStateOf<PlaybackChoice?>(null) }
    val automatic = stringResource(R.string.settings_playback_auto)
    val off = stringResource(R.string.settings_playback_subtitles_off)
    Column {
        SectionLabel(
            stringResource(R.string.settings_playback),
            Modifier.padding(start = 10.dp, top = 18.dp, bottom = 4.dp),
        )
        Text(
            stringResource(R.string.settings_playback_help),
            style = OntTheme.type.meta,
            color = OntTheme.colors.textMuted,
            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
        )
        OntListItem(
            text = stringResource(R.string.settings_playback_audio),
            selected = false,
            onClick = { choosing = PlaybackChoice.AUDIO },
            trailingTag = audio?.let(::languageLabel) ?: automatic,
        )
        OntListItem(
            text = stringResource(R.string.settings_playback_subtitles),
            selected = false,
            onClick = { choosing = PlaybackChoice.SUBTITLES },
            trailingTag = subtitles?.let(::languageLabel) ?: off,
        )
        OntListItem(
            text = stringResource(R.string.settings_playback_format),
            selected = false,
            onClick = { choosing = PlaybackChoice.FORMAT },
            trailingTag = stringResource(format.label),
        )
        OntListItem(
            text = stringResource(R.string.settings_playback_timeshift),
            selected = false,
            onClick = { settings.setTimeshift(!timeshift) },
            trailingTag = stringResource(if (timeshift) R.string.settings_on else R.string.settings_off),
        )
        Text(
            stringResource(R.string.settings_playback_timeshift_help),
            style = OntTheme.type.meta,
            color = OntTheme.colors.textMuted,
            modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
        )
    }
    val close = { choosing = null }
    when (choosing) {
        PlaybackChoice.AUDIO -> {
            OntMenu(
                title = stringResource(R.string.settings_playback_audio),
                options =
                    listOf(OntMenuOption(automatic) { settings.setAudioLanguage(null) }) +
                        LANGUAGES.map { tag -> OntMenuOption(languageLabel(tag)) { settings.setAudioLanguage(tag) } },
                onDismiss = close,
            )
        }

        PlaybackChoice.SUBTITLES -> {
            OntMenu(
                title = stringResource(R.string.settings_playback_subtitles),
                options =
                    listOf(OntMenuOption(off) { settings.setSubtitleLanguage(null) }) +
                        LANGUAGES.map { tag ->
                            OntMenuOption(languageLabel(tag)) { settings.setSubtitleLanguage(tag) }
                        },
                onDismiss = close,
            )
        }

        PlaybackChoice.FORMAT -> {
            val labels = PictureFormat.entries.map { stringResource(it.label) }
            OntMenu(
                title = stringResource(R.string.settings_playback_format),
                options =
                    PictureFormat.entries.mapIndexed { i, choice ->
                        OntMenuOption(labels[i]) { settings.setFormat(choice) }
                    },
                onDismiss = close,
            )
        }

        null -> {}
    }
}

private fun languageLabel(tag: String): String {
    val locale = Locale.getDefault()
    return Locale.forLanguageTag(tag).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}
