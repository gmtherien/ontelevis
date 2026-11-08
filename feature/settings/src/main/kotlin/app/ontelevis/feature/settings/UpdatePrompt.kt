package app.ontelevis.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.ontelevis.core.data.UpdateError
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class UpdateViewModel
    @Inject
    constructor(
        val center: UpdateCenter,
    ) : ViewModel()

/**
 * The update dialog, placed once at the root of the app. Checks GitHub once per start; while
 * [allowed] is false (a full-screen player is open) nothing shows: the dialog waits.
 */
@Composable
fun UpdatePrompt(
    allowed: Boolean,
    viewModel: UpdateViewModel = hiltViewModel(),
) {
    val center = viewModel.center
    val state by center.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { center.startupCheck() }
    if (!allowed) return
    when (val s = state) {
        UpdateState.Idle, UpdateState.Checking -> {}

        is UpdateState.Available -> {
            val notes = s.update.notes.toPlainNotes()
            OntMenu(
                title =
                    stringResource(R.string.update_available, s.update.version.toString(), center.versionName) +
                        if (notes.isEmpty()) "" else "\n\n$notes",
                options =
                    listOf(
                        OntMenuOption(stringResource(R.string.update_now)) { center.update(s.update) },
                        OntMenuOption(stringResource(R.string.update_later)) { center.dismiss() },
                    ),
                onDismiss = center::dismiss,
            )
        }

        is UpdateState.Downloading -> {
            val percent = if (s.progress < 0) "" else " ${(s.progress * 100).toInt()} %"
            OntMenu(
                title = stringResource(R.string.update_downloading, s.update.version.toString()) + percent,
                options = listOf(OntMenuOption(stringResource(R.string.update_cancel)) { center.dismiss() }),
                onDismiss = {},
                dismissOnSelect = false,
            )
        }

        is UpdateState.NeedsPermission -> {
            OntMenu(
                title = stringResource(R.string.update_permission),
                options =
                    listOf(
                        OntMenuOption(stringResource(R.string.update_retry)) { center.install(s.file) },
                        OntMenuOption(stringResource(R.string.update_cancel)) { center.dismiss() },
                    ),
                onDismiss = center::dismiss,
                dismissOnSelect = false,
            )
        }

        UpdateState.UpToDate -> {
            OntMenu(
                title = stringResource(R.string.update_up_to_date, center.versionName),
                options = listOf(OntMenuOption(stringResource(R.string.update_ok)) { center.dismiss() }),
                onDismiss = center::dismiss,
            )
        }

        is UpdateState.Failed -> {
            OntMenu(
                title =
                    stringResource(
                        when (s.error) {
                            UpdateError.NETWORK -> R.string.update_error_network
                            UpdateError.STORAGE -> R.string.update_error_storage
                            UpdateError.INVALID -> R.string.update_error_invalid
                        },
                    ),
                options = listOf(OntMenuOption(stringResource(R.string.update_ok)) { center.dismiss() }),
                onDismiss = center::dismiss,
            )
        }
    }
}

/** Release notes are markdown: keep a few plain lines for the dialog. */
private fun String.toPlainNotes(): String =
    lines()
        .map {
            it
                .trim()
                .removePrefix(
                    "#",
                ).removePrefix("#")
                .removePrefix("#")
                .trim()
                .removePrefix("* ")
                .removePrefix("- ")
        }.filter { it.isNotEmpty() && !it.startsWith("**Full Changelog**") }
        .take(4)
        .joinToString("\n")
