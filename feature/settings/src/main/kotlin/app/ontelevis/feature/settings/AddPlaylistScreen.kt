package app.ontelevis.feature.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import app.ontelevis.core.designsystem.component.LocalIsTv
import app.ontelevis.core.designsystem.component.OntListItem
import app.ontelevis.core.designsystem.component.OntPrimaryButton
import app.ontelevis.core.designsystem.component.OntTextField
import app.ontelevis.core.designsystem.component.OntTopBar
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.RefreshError
import app.ontelevis.feature.settings.AddPlaylistViewModel.Companion.FIELD_EPG
import app.ontelevis.feature.settings.AddPlaylistViewModel.Companion.FIELD_PASSWORD
import app.ontelevis.feature.settings.AddPlaylistViewModel.Companion.FIELD_SERVER
import app.ontelevis.feature.settings.AddPlaylistViewModel.Companion.FIELD_URL
import app.ontelevis.feature.settings.AddPlaylistViewModel.Companion.FIELD_USERNAME

@Composable
fun AddPlaylistScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddPlaylistViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val c = OntTheme.colors
    val tv = LocalIsTv.current
    val firstFocus = remember { FocusRequester() }

    LaunchedEffect(form.done) { if (form.done) onDone() }
    if (tv) LaunchedEffect(Unit) { firstFocus.requestFocus() }

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .verticalScroll(rememberScrollState())
            .padding(
                start = if (tv) OntDimens.ContentStartPadding + 16.dp else 16.dp,
                end = if (tv) OntDimens.ContentEndPadding else 16.dp,
                top = if (tv) OntDimens.SafeVertical else 16.dp,
                bottom = OntDimens.SafeVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OntTopBar(
            listOf(stringResource(R.string.feature_settings_title), stringResource(R.string.settings_add_playlist)),
        )
        Column(Modifier.widthIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OntListItem(
                    text = stringResource(R.string.settings_kind_m3u),
                    selected = form.kind == PlaylistKind.M3U,
                    onClick = { viewModel.update { copy(kind = PlaylistKind.M3U) } },
                    modifier = Modifier.weight(1f).focusRequester(firstFocus),
                )
                OntListItem(
                    text = stringResource(R.string.settings_kind_xtream),
                    selected = form.kind == PlaylistKind.XTREAM,
                    onClick = { viewModel.update { copy(kind = PlaylistKind.XTREAM) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Text(
                stringResource(
                    if (form.kind ==
                        PlaylistKind.M3U
                    ) {
                        R.string.settings_m3u_help
                    } else {
                        R.string.settings_xtream_help
                    },
                ),
                style = OntTheme.type.body,
                color = c.textMuted,
            )
            OntTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { copy(name = v) } },
                label = stringResource(R.string.settings_field_name),
                placeholder = stringResource(R.string.settings_field_name_hint),
            )
            when (form.kind) {
                PlaylistKind.M3U -> {
                    OntTextField(
                        value = form.url,
                        onValueChange = { v -> viewModel.update { copy(url = v) } },
                        label = stringResource(R.string.settings_field_url),
                        placeholder = "https://",
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        error = form.errors[FIELD_URL]?.let { stringResource(it.text) },
                    )
                    OntTextField(
                        value = form.epgUrl,
                        onValueChange = { v -> viewModel.update { copy(epgUrl = v) } },
                        label = stringResource(R.string.settings_field_epg),
                        placeholder = stringResource(R.string.settings_field_epg_hint),
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Done,
                        error = form.errors[FIELD_EPG]?.let { stringResource(it.text) },
                    )
                }

                PlaylistKind.XTREAM -> {
                    OntTextField(
                        value = form.server,
                        onValueChange = { v -> viewModel.update { copy(server = v) } },
                        label = stringResource(R.string.settings_field_server),
                        placeholder = "http://",
                        keyboardType = KeyboardType.Uri,
                        error = form.errors[FIELD_SERVER]?.let { stringResource(it.text) },
                    )
                    OntTextField(
                        value = form.username,
                        onValueChange = { v -> viewModel.update { copy(username = v) } },
                        label = stringResource(R.string.settings_field_username),
                        error = form.errors[FIELD_USERNAME]?.let { stringResource(it.text) },
                    )
                    OntTextField(
                        value = form.password,
                        onValueChange = { v -> viewModel.update { copy(password = v) } },
                        label = stringResource(R.string.settings_field_password),
                        keyboardType = KeyboardType.Password,
                        password = true,
                        imeAction = ImeAction.Done,
                        error = form.errors[FIELD_PASSWORD]?.let { stringResource(it.text) },
                    )
                }
            }
            form.failure?.let { failure ->
                Text(stringResource(refreshErrorText(failure)), style = OntTheme.type.body, color = c.live)
            }
            OntPrimaryButton(onClick = viewModel::submit) {
                Text(
                    stringResource(if (form.saving) R.string.settings_loading_playlist else R.string.settings_add),
                    style = OntTheme.type.button,
                )
            }
        }
    }
}

private val FieldError.text: Int
    @StringRes get() =
        when (this) {
            FieldError.REQUIRED -> R.string.settings_error_required
            FieldError.INVALID_URL -> R.string.settings_error_url
        }

@StringRes
internal fun refreshErrorText(error: RefreshError): Int =
    when (error) {
        RefreshError.NETWORK -> R.string.settings_error_network
        RefreshError.HTTP -> R.string.settings_error_http
        RefreshError.AUTH -> R.string.settings_error_auth
        RefreshError.FORMAT -> R.string.settings_error_format
        RefreshError.EMPTY -> R.string.settings_error_empty
    }
