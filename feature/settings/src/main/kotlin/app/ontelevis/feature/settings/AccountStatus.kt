package app.ontelevis.feature.settings

import android.content.Context
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.core.content.edit
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.ontelevis.core.data.PlaylistRepository
import app.ontelevis.core.model.Playlist
import app.ontelevis.core.model.XtreamAccount
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** "Expires 12 Mar 2027 (5 days) · 1/2 connections · Trial", and whether it should be shown as a warning. */
@Composable
internal fun accountLine(
    account: XtreamAccount,
    now: Long = System.currentTimeMillis(),
): Pair<String, Boolean> {
    val days = account.daysLeft(now)
    val expiresAt = account.expiresAt
    val expiry =
        when {
            !account.isActive -> {
                stringResource(R.string.settings_account_status, account.status.orEmpty())
            }

            expiresAt == null -> {
                stringResource(R.string.settings_account_never)
            }

            days != null && days < 0 -> {
                stringResource(R.string.settings_account_expired)
            }

            else -> {
                val date = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(expiresAt))
                val left =
                    days
                        ?.takeIf { it <= XtreamAccount.WARN_DAYS }
                        ?.toInt()
                        ?.let {
                            " (" + pluralStringResource(R.plurals.settings_account_days_left, it, it) + ")"
                        }.orEmpty()
                stringResource(R.string.settings_account_expires, date) + left
            }
        }
    val parts =
        listOfNotNull(
            expiry,
            account.maxConnections.takeIf { it > 0 }?.let {
                stringResource(R.string.settings_account_connections, account.activeConnections, it)
            },
            stringResource(R.string.settings_account_trial).takeIf { account.isTrial },
        )
    return parts.joinToString(" · ") to account.needsWarning(now)
}

/**
 * At start: reads the Xtream accounts not checked for a while, then, once a day, tells the user when
 * a subscription expires within a week, has expired or is no longer active.
 */
@Composable
fun AccountWarning(viewModel: AccountWarningViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val expires = stringResource(R.string.account_warning_expires)
    val expired = stringResource(R.string.account_warning_expired)
    LaunchedEffect(Unit) {
        viewModel.warnings().forEach { (playlist, days) ->
            val text =
                when {
                    days == null || days < 0 -> {
                        expired.format(playlist.name)
                    }

                    days == 0L -> {
                        expires.format(playlist.name)
                    }

                    else -> {
                        context.resources.getQuantityString(
                            R.plurals.account_warning_days,
                            days.toInt(),
                            playlist.name,
                            days,
                        )
                    }
                }
            Toast.makeText(context, text, Toast.LENGTH_LONG).show()
        }
    }
}

@HiltViewModel
class AccountWarningViewModel
    @Inject
    constructor(
        private val repository: PlaylistRepository,
        @ApplicationContext context: Context,
    ) : ViewModel() {
        private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

        /** Playlists to warn about now with their days left (null: expired or not active); empty once warned today. */
        suspend fun warnings(): List<Pair<Playlist, Long?>> {
            if (shownThisProcess) return emptyList()
            shownThisProcess = true
            repository.checkAccounts(TimeUnit.HOURS.toMillis(CHECK_EVERY_HOURS))
            val today = LocalDate.now().toString()
            if (prefs.getString(KEY_LAST_WARNING, null) == today) return emptyList()
            val now = System.currentTimeMillis()
            val list =
                repository.observePlaylists().first().filter { it.enabled }.mapNotNull { playlist ->
                    val account = playlist.account ?: return@mapNotNull null
                    if (!account.needsWarning(now)) return@mapNotNull null
                    playlist to account.daysLeft(now)?.takeIf { account.isActive }
                }
            if (list.isNotEmpty()) prefs.edit { putString(KEY_LAST_WARNING, today) }
            return list
        }

        private companion object {
            const val KEY_LAST_WARNING = "last_warning_day"
            const val CHECK_EVERY_HOURS = 6L

            /** One check per app process (the composable is recreated with the activity). */
            var shownThisProcess = false
        }
    }
