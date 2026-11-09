package app.ontelevis.feature.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.Environment
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import app.ontelevis.core.data.BackupFiles
import app.ontelevis.core.designsystem.component.LocalIsTv
import app.ontelevis.core.designsystem.component.OntCreatePinDialog
import app.ontelevis.core.designsystem.component.OntIconButton
import app.ontelevis.core.designsystem.component.OntListItem
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import app.ontelevis.core.designsystem.component.OntTopBar
import app.ontelevis.core.designsystem.component.OntVerifyPinDialog
import app.ontelevis.core.designsystem.component.SectionLabel
import app.ontelevis.core.designsystem.theme.OntDimens
import app.ontelevis.core.designsystem.theme.OntTheme
import app.ontelevis.core.model.Playlist
import app.ontelevis.core.model.PlaylistSource
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import app.ontelevis.core.designsystem.R as DesignR

/** Settings. V1 step 1: the playlist list (refresh, delete) and "Add a playlist". */
@Composable
fun SettingsScreen(
    onAddPlaylist: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val language by viewModel.language.collectAsStateWithLifecycle()
    val hasPin by viewModel.hasPin.collectAsStateWithLifecycle()
    val betas by viewModel.updates.betas.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pinStep by remember { mutableStateOf(PinStep.NONE) }
    val pendingRestore by viewModel.pendingRestore.collectAsStateWithLifecycle()
    val downloadBackups by viewModel.downloadBackups.collectAsStateWithLifecycle()
    val backupMessage by viewModel.backupMessage.collectAsStateWithLifecycle()
    // Restoring replaces the PIN too: with a PIN set, it must be entered first.
    var restorePinCheck by remember { mutableStateOf(false) }
    val picker = remember { viewModel.backupFiles.hasPicker() }
    val createDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(BackupFiles.MIME)) { uri ->
            uri?.let(viewModel::saveTo)
        }
    val openDocument =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { viewModel.open(it, it.lastPathSegment.orEmpty()) }
        }
    // Before Android 10, Downloads needs the storage permission; the action runs once granted.
    var afterPermission by remember { mutableStateOf<(() -> Unit)?>(null) }
    val storagePermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) afterPermission?.invoke()
            afterPermission = null
        }

    fun withStorage(action: () -> Unit) {
        val needed = viewModel.backupFiles.needsStoragePermission()
        val granted =
            !needed ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        if (granted) {
            action()
        } else {
            afterPermission = action
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
    }

    fun startRestore() {
        if (picker) {
            openDocument.launch(
                arrayOf(BackupFiles.MIME, "text/plain", "*/*"),
            )
        } else {
            withStorage(viewModel::listDownloads)
        }
    }
    val messages =
        mapOf(
            "saved" to stringResource(R.string.settings_backup_saved),
            "savedIn" to stringResource(R.string.settings_backup_saved_in),
            "restored" to stringResource(R.string.settings_backup_restored),
            "unreadable" to stringResource(R.string.settings_backup_unreadable),
            "failed" to stringResource(R.string.settings_backup_failed),
        )
    LaunchedEffect(backupMessage) {
        val message = backupMessage ?: return@LaunchedEffect
        viewModel.consumeBackupMessage()
        val text =
            when (message) {
                is BackupMessage.Saved -> {
                    message.folder?.let { messages.getValue("savedIn").format(it) }
                        ?: messages.getValue("saved")
                }

                is BackupMessage.Restored -> {
                    messages.getValue("restored")
                }

                BackupMessage.Unreadable -> {
                    messages.getValue("unreadable")
                }

                BackupMessage.Failed -> {
                    messages.getValue("failed")
                }
            }
        Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        if (message is BackupMessage.Restored && message.recreate) context.findActivity()?.recreate()
    }
    // The PIN rows change when a PIN is created or removed: focus goes to the row that replaces them.
    val pinRowFocus = remember { FocusRequester() }
    var focusPinRow by remember { mutableStateOf(false) }
    LaunchedEffect(hasPin, focusPinRow) {
        if (focusPinRow) {
            withFrameNanos { }
            runCatching { pinRowFocus.requestFocus() }
            focusPinRow = false
        }
    }
    val c = OntTheme.colors
    val tv = LocalIsTv.current
    val addFocus = remember { FocusRequester() }
    val title = stringResource(R.string.feature_settings_title)

    Column(
        modifier
            .fillMaxSize()
            .background(c.background)
            .padding(
                start = if (tv) OntDimens.ContentStartPadding + 16.dp else 16.dp,
                end = if (tv) OntDimens.ContentEndPadding else 16.dp,
                top = if (tv) OntDimens.SafeVertical else 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        OntTopBar(listOf(title, stringResource(R.string.settings_playlists)))
        LazyColumn(
            modifier = Modifier.widthIn(max = 640.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item {
                SectionLabel(
                    stringResource(R.string.settings_playlists),
                    Modifier.padding(start = 10.dp, bottom = 4.dp),
                )
            }
            items(playlists.orEmpty(), key = { it.id }) { playlist ->
                PlaylistRow(
                    playlist = playlist,
                    refreshing = playlist.id in refreshing,
                    onRefresh = { viewModel.refresh(playlist.id) },
                    onDelete = {
                        // Move focus before the row disappears, or it falls back to the rail.
                        runCatching { addFocus.requestFocus() }
                        viewModel.delete(playlist.id)
                    },
                )
            }
            item {
                OntListItem(
                    text = stringResource(R.string.settings_add_playlist),
                    selected = false,
                    onClick = onAddPlaylist,
                    leadingIcon = { tint ->
                        Icon(
                            painterResource(DesignR.drawable.ont_ic_plus),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                    modifier = Modifier.focusRequester(addFocus),
                )
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_parental),
                    Modifier.padding(start = 10.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.settings_parental_help),
                    style = OntTheme.type.meta,
                    color = c.textMuted,
                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                )
            }
            if (!hasPin) {
                item {
                    OntListItem(
                        text = stringResource(R.string.settings_pin_create),
                        selected = false,
                        onClick = { pinStep = PinStep.CREATE },
                        leadingIcon = { tint -> LockIcon(tint) },
                        modifier = Modifier.focusRequester(pinRowFocus),
                    )
                }
            } else {
                item {
                    OntListItem(
                        text = stringResource(R.string.settings_pin_change),
                        selected = false,
                        onClick = { pinStep = PinStep.CHANGE_VERIFY },
                        leadingIcon = { tint -> LockIcon(tint) },
                        modifier = Modifier.focusRequester(pinRowFocus),
                    )
                }
                item {
                    OntListItem(
                        text = stringResource(R.string.settings_pin_remove),
                        selected = false,
                        onClick = { pinStep = PinStep.REMOVE_VERIFY },
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_backup),
                    Modifier.padding(start = 10.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            item {
                Text(
                    stringResource(R.string.settings_backup_help),
                    style = OntTheme.type.meta,
                    color = c.textMuted,
                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                )
            }
            item {
                OntListItem(
                    text = stringResource(R.string.settings_backup_save),
                    selected = false,
                    onClick = {
                        if (picker) {
                            createDocument.launch(viewModel.backupFiles.suggestedName())
                        } else {
                            withStorage(viewModel::saveToDownloads)
                        }
                    },
                )
            }
            item {
                OntListItem(
                    text = stringResource(R.string.settings_backup_restore),
                    selected = false,
                    onClick = { if (hasPin) restorePinCheck = true else startRestore() },
                )
            }
            item { PlaybackSettingsSection(viewModel.player) }
            item {
                SectionLabel(
                    stringResource(R.string.settings_app),
                    Modifier.padding(start = 10.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            item {
                Text(
                    stringResource(
                        if (viewModel.updates.canUpdate) R.string.settings_version else R.string.settings_version_dev,
                        viewModel.updates.versionName,
                    ),
                    style = OntTheme.type.meta,
                    color = c.textMuted,
                    modifier = Modifier.padding(start = 10.dp, bottom = 4.dp),
                )
            }
            if (viewModel.updates.canUpdate) {
                item {
                    OntListItem(
                        text = stringResource(R.string.settings_check_updates),
                        selected = false,
                        onClick = viewModel.updates::manualCheck,
                    )
                }
                item {
                    OntListItem(
                        text = stringResource(R.string.settings_betas),
                        selected = false,
                        onClick = { viewModel.updates.setBetas(!betas) },
                        trailingTag = stringResource(if (betas) R.string.settings_on else R.string.settings_off),
                    )
                }
            }
            item {
                SectionLabel(
                    stringResource(R.string.settings_language),
                    Modifier.padding(start = 10.dp, top = 18.dp, bottom = 4.dp),
                )
            }
            items(listOf("") + viewModel.languages) { tag ->
                OntListItem(
                    text = if (tag.isEmpty()) stringResource(R.string.settings_language_system) else languageName(tag),
                    selected = tag == language,
                    onClick = {
                        if (viewModel.setLanguage(tag)) context.findActivity()?.recreate()
                    },
                )
            }
        }
    }
    if (restorePinCheck) {
        OntVerifyPinDialog(
            title = stringResource(R.string.settings_backup_restore),
            check = viewModel::checkPin,
            onSuccess = {
                restorePinCheck = false
                startRestore()
            },
            onDismiss = { restorePinCheck = false },
        )
    }
    downloadBackups?.let { files ->
        if (files.isEmpty()) {
            OntMenu(
                title =
                    stringResource(
                        R.string.settings_backup_none,
                        "${Environment.DIRECTORY_DOWNLOADS}/${BackupFiles.FOLDER}",
                    ),
                options = listOf(OntMenuOption(stringResource(R.string.settings_backup_close)) {}),
                onDismiss = viewModel::closeDownloads,
            )
        } else {
            val dates = remember { DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT) }
            OntMenu(
                title = stringResource(R.string.settings_backup_pick),
                options =
                    files.take(MAX_LISTED).map { file ->
                        OntMenuOption(dates.format(Date(file.modifiedAt))) { viewModel.open(file.uri, file.name) }
                    },
                onDismiss = viewModel::closeDownloads,
            )
        }
    }
    pendingRestore?.let { pending ->
        val summary = pending.summary
        OntMenu(
            title =
                stringResource(
                    R.string.settings_backup_confirm,
                    summary.playlists.joinToString(", ").ifEmpty { "-" },
                    summary.favorites,
                ),
            options =
                listOf(
                    OntMenuOption(
                        stringResource(R.string.settings_backup_confirm_cancel),
                    ) { viewModel.cancelRestore() },
                    OntMenuOption(stringResource(R.string.settings_backup_confirm_ok)) { viewModel.confirmRestore() },
                ),
            onDismiss = viewModel::cancelRestore,
        )
    }
    val pinTitle = stringResource(R.string.settings_parental)
    when (pinStep) {
        PinStep.NONE -> {}

        PinStep.CREATE, PinStep.CHANGE_NEW -> {
            OntCreatePinDialog(
                title = pinTitle,
                onCreated = {
                    viewModel.setPin(it)
                    pinStep = PinStep.NONE
                    focusPinRow = true
                },
                onDismiss = {
                    pinStep = PinStep.NONE
                    focusPinRow = true
                },
            )
        }

        PinStep.CHANGE_VERIFY -> {
            OntVerifyPinDialog(
                title = pinTitle,
                check = viewModel::checkPin,
                onSuccess = { pinStep = PinStep.CHANGE_NEW },
                onDismiss = { pinStep = PinStep.NONE },
            )
        }

        PinStep.REMOVE_VERIFY -> {
            OntVerifyPinDialog(
                title = pinTitle,
                check = viewModel::checkPin,
                onSuccess = {
                    viewModel.clearPin()
                    pinStep = PinStep.NONE
                    focusPinRow = true
                },
                onDismiss = { pinStep = PinStep.NONE },
            )
        }
    }
    val loaded = playlists != null
    if (tv) LaunchedEffect(loaded) { if (loaded) runCatching { addFocus.requestFocus() } }
}

@Composable
private fun PlaylistRow(
    playlist: Playlist,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
) {
    val c = OntTheme.colors
    var confirmDelete by remember { mutableStateOf(false) }
    // A second press within a few seconds confirms the deletion (no dialog to dismiss on a remote).
    LaunchedEffect(confirmDelete) {
        if (confirmDelete) {
            delay(4_000)
            confirmDelete = false
        }
    }
    val kind =
        stringResource(
            if (playlist.source is PlaylistSource.Xtream) R.string.settings_kind_xtream else R.string.settings_kind_m3u,
        )
    val error = playlist.lastError
    val status =
        when {
            refreshing -> {
                stringResource(R.string.settings_refreshing)
            }

            error != null -> {
                stringResource(refreshErrorText(error))
            }

            else -> {
                val channels =
                    pluralStringResource(R.plurals.settings_channel_count, playlist.channelCount, playlist.channelCount)
                val guide =
                    when {
                        playlist.epgError != null -> {
                            stringResource(R.string.settings_guide_failed)
                        }

                        playlist.lastEpgAt != null -> {
                            pluralStringResource(
                                R.plurals.settings_programme_count,
                                playlist.programmeCount,
                                playlist.programmeCount,
                            )
                        }

                        else -> {
                            null
                        }
                    }
                listOfNotNull(
                    channels,
                    playlist.movieCount.takeIf { it > 0 }?.let {
                        pluralStringResource(R.plurals.settings_movie_count, it, it)
                    },
                    playlist.seriesCount.takeIf { it > 0 }?.let {
                        pluralStringResource(R.plurals.settings_series_count, it, it)
                    },
                    guide,
                ).joinToString(" · ")
            }
        }
    Column {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(c.surface1, RoundedCornerShape(OntDimens.RadiusButton))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    playlist.name,
                    style = OntTheme.type.itemStrong,
                    color = c.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "$kind · $status",
                    style = OntTheme.type.meta,
                    color = if (error != null && !refreshing) c.live else c.textMuted,
                    maxLines = 2,
                )
                playlist.account?.let { account ->
                    val (line, warn) = accountLine(account)
                    Text(
                        line,
                        style = OntTheme.type.meta,
                        color = if (warn) c.live else c.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            OntIconButton(onClick = onRefresh) {
                Icon(
                    painterResource(DesignR.drawable.ont_ic_arrows_clockwise),
                    contentDescription = stringResource(R.string.settings_refresh),
                    modifier = Modifier.size(18.dp),
                )
            }
            OntIconButton(onClick = { if (confirmDelete) onDelete() else confirmDelete = true }) {
                Icon(
                    painterResource(DesignR.drawable.ont_ic_trash),
                    contentDescription = stringResource(R.string.settings_delete),
                    tint = if (confirmDelete) c.live else Color.Unspecified,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (confirmDelete) {
            Text(
                stringResource(R.string.settings_delete_confirm),
                style = OntTheme.type.meta,
                color = c.live,
                modifier = Modifier.padding(start = 14.dp, top = 4.dp),
            )
        }
    }
}

/** Backups listed from Downloads (the newest). */
private const val MAX_LISTED = 8

/** Where the parental PIN dialogs are. */
private enum class PinStep { NONE, CREATE, CHANGE_VERIFY, CHANGE_NEW, REMOVE_VERIFY }

@Composable
private fun LockIcon(tint: androidx.compose.ui.graphics.Color) {
    Icon(
        painterResource(DesignR.drawable.ont_ic_lock),
        contentDescription = null,
        tint = tint,
        modifier = Modifier.size(16.dp),
    )
}

/** A language's name in that language ("Français"), as language pickers usually show it. */
private fun languageName(tag: String): String {
    val locale = Locale.forLanguageTag(tag)
    return locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
}

private fun Context.findActivity(): Activity? =
    generateSequence(this) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
