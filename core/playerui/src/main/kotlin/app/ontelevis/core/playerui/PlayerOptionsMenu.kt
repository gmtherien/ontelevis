package app.ontelevis.core.playerui

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import app.ontelevis.core.model.PictureFormat
import app.ontelevis.core.player.LivePlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PlayerOptionsViewModel
    @Inject
    constructor(
        val options: PlaybackOptions,
        val sleepTimer: SleepTimer,
        val livePlayer: LivePlayer,
    ) : ViewModel()

private enum class Page { MAIN, AUDIO, SUBTITLES, FORMAT, SLEEP }

/**
 * The player's options (Menu key, or OK on the info bar): audio and subtitle tracks, picture format,
 * stream info and sleep timer. Tracks and format are saved for the channel or title playing.
 */
@Composable
fun PlayerOptionsMenu(
    live: Boolean,
    onDismiss: () -> Unit,
    viewModel: PlayerOptionsViewModel = hiltViewModel(),
) {
    val session = if (live) viewModel.options.live else viewModel.options.vod
    val settings = viewModel.options.settings
    val format by session.format.collectAsStateWithLifecycle()
    val showStats by settings.showStats.collectAsStateWithLifecycle()
    val sleepEndsAt by viewModel.sleepTimer.endsAt.collectAsStateWithLifecycle()
    val liveState by viewModel.livePlayer.state.collectAsStateWithLifecycle()
    var page by remember { mutableStateOf(Page.MAIN) }
    val unnamed = stringResource(R.string.player_track_number)
    val back = { page = Page.MAIN }

    when (page) {
        Page.MAIN -> {
            val audio = session.audioTracks(unnamed)
            val subtitles = session.subtitleTracks(unnamed)
            val audioValue = audio.firstOrNull { it.selected }?.label ?: stringResource(R.string.player_auto)
            val subtitleValue =
                subtitles.firstOrNull { it.selected }?.label ?: stringResource(R.string.player_subtitles_off)
            val sleepValue =
                viewModel.sleepTimer.minutesLeft()?.let { pluralStringResource(R.plurals.sleep_left, it, it) }
                    ?: stringResource(R.string.sleep_off)
            // Live pause, for remotes without Play/Pause and for touch screens.
            val pauseOptions =
                if (!live) {
                    emptyList()
                } else {
                    listOfNotNull(
                        OntMenuOption(
                            stringResource(if (liveState.paused) R.string.player_resume else R.string.player_pause),
                        ) {
                            viewModel.livePlayer.togglePause()
                            onDismiss()
                        }.takeIf { viewModel.livePlayer.canPause || liveState.paused },
                        OntMenuOption(stringResource(R.string.player_back_to_live)) {
                            viewModel.livePlayer.backToLive()
                            onDismiss()
                        }.takeIf { liveState.paused || liveState.behindMs > 0 },
                    )
                }
            OntMenu(
                title = stringResource(R.string.player_options),
                options =
                    pauseOptions +
                        listOf(
                            OntMenuOption(value(R.string.player_audio, audioValue)) { page = Page.AUDIO },
                            OntMenuOption(value(R.string.player_subtitles, subtitleValue)) { page = Page.SUBTITLES },
                            OntMenuOption(value(R.string.player_format, stringResource(format.label))) {
                                page = Page.FORMAT
                            },
                            OntMenuOption(
                                value(
                                    R.string.player_stats,
                                    stringResource(if (showStats) R.string.player_shown else R.string.player_hidden),
                                ),
                            ) { settings.setShowStats(!showStats) },
                            OntMenuOption(value(R.string.player_sleep, sleepValue)) { page = Page.SLEEP },
                        ),
                onDismiss = onDismiss,
                dismissOnSelect = false,
            )
        }

        Page.AUDIO -> {
            val tracks = session.audioTracks(unnamed)
            OntMenu(
                title = stringResource(R.string.player_audio),
                options =
                    tracks
                        .map { track ->
                            OntMenuOption(check(track.selected) + track.label) {
                                session.selectAudio(track)
                                onDismiss()
                            }
                        }.ifEmpty { listOf(OntMenuOption(stringResource(R.string.player_no_tracks), back)) },
                onDismiss = back,
                dismissOnSelect = false,
            )
        }

        Page.SUBTITLES -> {
            val tracks = session.subtitleTracks(unnamed)
            val none = tracks.none { it.selected }
            OntMenu(
                title = stringResource(R.string.player_subtitles),
                options =
                    listOf(
                        OntMenuOption(check(none) + stringResource(R.string.player_subtitles_off)) {
                            session.selectSubtitles(null)
                            onDismiss()
                        },
                    ) +
                        tracks.map { track ->
                            OntMenuOption(check(track.selected) + track.label) {
                                session.selectSubtitles(track)
                                onDismiss()
                            }
                        },
                onDismiss = back,
                dismissOnSelect = false,
            )
        }

        Page.FORMAT -> {
            OntMenu(
                title = stringResource(R.string.player_format),
                options =
                    PictureFormat.entries.map { choice ->
                        OntMenuOption(check(choice == format) + stringResource(choice.label)) {
                            session.setFormat(choice)
                            onDismiss()
                        }
                    },
                onDismiss = back,
                dismissOnSelect = false,
            )
        }

        Page.SLEEP -> {
            val off = sleepEndsAt == null
            // Development builds also offer 1 minute, to test the end of the timer.
            val debuggable = LocalContext.current.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
            val choices = if (debuggable) listOf(1) + SleepTimer.CHOICES else SleepTimer.CHOICES
            OntMenu(
                title = stringResource(R.string.player_sleep),
                options =
                    listOf(
                        OntMenuOption(check(off) + stringResource(R.string.sleep_off)) {
                            viewModel.sleepTimer.set(null)
                            onDismiss()
                        },
                    ) +
                        choices.map { minutes ->
                            OntMenuOption(pluralStringResource(R.plurals.sleep_minutes, minutes, minutes)) {
                                viewModel.sleepTimer.set(minutes)
                                onDismiss()
                            }
                        },
                onDismiss = back,
                dismissOnSelect = false,
            )
        }
    }
}

@Composable
private fun value(
    name: Int,
    value: String,
) = stringResource(R.string.player_option_value, stringResource(name), value)

private fun check(selected: Boolean) = if (selected) "✓  " else ""

val PictureFormat.label: Int
    get() =
        when (this) {
            PictureFormat.FIT -> R.string.format_fit
            PictureFormat.FILL -> R.string.format_fill
            PictureFormat.ZOOM -> R.string.format_zoom
        }
