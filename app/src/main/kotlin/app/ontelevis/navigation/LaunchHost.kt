package app.ontelevis.navigation

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import app.ontelevis.R
import app.ontelevis.core.data.AppSettings
import app.ontelevis.core.data.CategoryKind
import app.ontelevis.core.data.CategoryRef
import app.ontelevis.core.data.ChannelRepository
import app.ontelevis.core.data.LaunchRequest
import app.ontelevis.core.data.LaunchRequests
import app.ontelevis.core.data.ParentalControl
import app.ontelevis.core.data.VodRepository
import app.ontelevis.core.data.reminder.Reminder
import app.ontelevis.core.data.reminder.ReminderCenter
import app.ontelevis.core.designsystem.component.OntMenu
import app.ontelevis.core.designsystem.component.OntMenuOption
import app.ontelevis.core.player.LivePlayer
import app.ontelevis.feature.home.HomeRoute
import app.ontelevis.feature.vod.MovieRoute
import app.ontelevis.feature.vod.SeriesRoute
import app.ontelevis.feature.vod.VodPlayerRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import java.util.Date
import javax.inject.Inject

/** Which screen the app opens on (Settings › Open at start), read once per app shell. */
@HiltViewModel
class StartViewModel
    @Inject
    constructor(
        settings: AppSettings,
    ) : ViewModel() {
        val startRoute: Any = if (settings.startOnHome.value) HomeRoute else LiveRoute
    }

@HiltViewModel
class LaunchViewModel
    @Inject
    constructor(
        val requests: LaunchRequests,
        val reminders: ReminderCenter,
        private val vod: VodRepository,
        private val channels: ChannelRepository,
        private val livePlayer: LivePlayer,
        private val parental: ParentalControl,
    ) : ViewModel() {
        /**
         * Plays a channel opened from outside. False when it cannot play now: gone, or in a category
         * locked by the PIN (the TV screen then opens on that category, where the PIN is asked).
         */
        suspend fun playChannel(id: String): Boolean {
            val channel = channels.channel(id) ?: return false
            requests.openGroup(channel.playlistId to channel.group)
            val locked = CategoryRef(CategoryKind.LIVE, channel.playlistId, channel.group) in parental.closed.first()
            if (locked) return false
            livePlayer.play(channel, channels.channelsAround(channel))
            return true
        }

        /**
         * What a voice search asks for: a channel named like it (played full screen), else a movie or
         * series (its page). False when nothing matches.
         */
        suspend fun resolve(query: String): Boolean {
            channels.findByName(query).firstOrNull()?.let {
                requests.post(LaunchRequest.PlayChannel(it.id))
                return true
            }
            val id =
                vod.searchMovies(query, limit = 1).firstOrNull()?.id
                    ?: vod.searchSeries(query, limit = 1).firstOrNull()?.id
                    ?: return false
            requests.post(LaunchRequest.OpenVod(id))
            return true
        }

        /** The page of a movie, a series or an episode (its series), null when the title is gone. */
        suspend fun routeOf(contentId: String): Any? =
            when {
                EPISODE in contentId -> vod.episode(contentId)?.seriesId?.let { SeriesRoute(it) }
                SERIES in contentId -> SeriesRoute(contentId)
                else -> vod.movie(contentId)?.let { MovieRoute(contentId) }
            }

        private companion object {
            const val EPISODE = "::e:"
            const val SERIES = "::s:"
        }
    }

/**
 * Drawn once at the root of the app: opens what the app was started for (a reminder's "Watch",
 * Google TV's Continue watching, a voice search), and shows reminders that go off while the app
 * is on screen.
 */
@Composable
fun LaunchHost(
    navController: NavHostController,
    viewModel: LaunchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val notFound = stringResource(R.string.search_not_found)
    // Collected one by one (not a LaunchedEffect keyed on the request): taking a request clears it, and
    // a restart there cancelled the search or the lookup that was running.
    LaunchedEffect(viewModel) {
        viewModel.requests.pending.collect { request ->
            handle(request, viewModel, navController) { query ->
                Toast.makeText(context, notFound.format(query), Toast.LENGTH_LONG).show()
            }
        }
    }
    var due by remember { mutableStateOf<Reminder?>(null) }

    LaunchedEffect(viewModel) { viewModel.reminders.due.collect { due = it } }
    due?.let { reminder ->
        OntMenu(
            title =
                stringResource(
                    R.string.reminder_now,
                    reminder.title,
                    DateFormat.getTimeFormat(LocalContext.current).format(Date(reminder.start)),
                    reminder.channelName,
                ),
            options =
                listOf(
                    OntMenuOption(stringResource(R.string.reminder_watch)) {
                        viewModel.requests.post(LaunchRequest.PlayChannel(reminder.channelId))
                    },
                    OntMenuOption(stringResource(R.string.reminder_close)) {},
                ),
            onDismiss = { due = null },
        )
    }
}

private suspend fun handle(
    request: LaunchRequest?,
    viewModel: LaunchViewModel,
    navController: NavHostController,
    notFound: (String) -> Unit,
) {
    when (request) {
        // Full screen, over the TV screen (Back returns to its guide).
        is LaunchRequest.PlayChannel -> {
            if (viewModel.requests.take(request)) {
                val playing = viewModel.playChannel(request.channelId)
                navController.goTo(LiveRoute)
                if (playing) navController.navigate(PlayerRoute)
            }
        }

        is LaunchRequest.PlayVod -> {
            if (viewModel.requests.take(request)) navController.navigate(VodPlayerRoute(request.contentId))
        }

        is LaunchRequest.OpenVod -> {
            if (viewModel.requests.take(request)) {
                viewModel.routeOf(request.contentId)?.let { navController.navigate(it) }
            }
        }

        is LaunchRequest.Search -> {
            if (viewModel.requests.take(request) && !viewModel.resolve(request.query)) notFound(request.query)
        }

        null -> {}
    }
}
