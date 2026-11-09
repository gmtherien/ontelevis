package app.ontelevis.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.ontelevis.core.data.CategoryKind
import app.ontelevis.core.data.CategoryRef
import app.ontelevis.core.data.ChannelRepository
import app.ontelevis.core.data.LaunchRequest
import app.ontelevis.core.data.LaunchRequests
import app.ontelevis.core.data.ParentalControl
import app.ontelevis.core.data.VodRepository
import app.ontelevis.core.model.Channel
import app.ontelevis.core.model.ContinueItem
import app.ontelevis.core.model.Movie
import app.ontelevis.core.model.Series
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** The rows of the Home screen; null until the first read. */
data class HomeUi(
    val continueItems: List<ContinueItem> = emptyList(),
    val channels: List<Channel> = emptyList(),
    val favoriteMovies: List<Movie> = emptyList(),
    val favoriteSeries: List<Series> = emptyList(),
    val recentMovies: List<Movie> = emptyList(),
    val recentSeries: List<Series> = emptyList(),
) {
    val isEmpty: Boolean
        get() =
            continueItems.isEmpty() && channels.isEmpty() && favoriteMovies.isEmpty() &&
                favoriteSeries.isEmpty() && recentMovies.isEmpty() && recentSeries.isEmpty()
}

@HiltViewModel
class HomeViewModel
    @Inject
    constructor(
        vod: VodRepository,
        channelRepository: ChannelRepository,
        parental: ParentalControl,
        private val requests: LaunchRequests,
    ) : ViewModel() {
        private val vodRows =
            combine(
                vod.observeContinueWatching(),
                vod.observeFavoriteMovies(),
                vod.observeFavoriteSeries(),
                vod.observeRecentMovies(),
                vod.observeRecentSeries(),
            ) { continueItems, favoriteMovies, favoriteSeries, recentMovies, recentSeries ->
                HomeUi(continueItems, emptyList(), favoriteMovies, favoriteSeries, recentMovies, recentSeries)
            }

        /** Rows without the content of categories locked by the PIN (as everywhere else in the app). */
        val ui: StateFlow<HomeUi?> =
            combine(vodRows, channelRepository.observeFavorites(), parental.closed) { rows, channels, closed ->
                fun open(
                    kind: CategoryKind,
                    playlistId: Long,
                    group: String,
                ) = CategoryRef(kind, playlistId, group) !in closed
                rows.copy(
                    continueItems =
                        rows.continueItems.filter {
                            open(
                                if (it.seriesId ==
                                    null
                                ) {
                                    CategoryKind.MOVIE
                                } else {
                                    CategoryKind.SERIES
                                },
                                it.playlistId,
                                it.group,
                            )
                        },
                    channels = channels.filter { open(CategoryKind.LIVE, it.playlistId, it.group) },
                    favoriteMovies = rows.favoriteMovies.filter { open(CategoryKind.MOVIE, it.playlistId, it.group) },
                    favoriteSeries = rows.favoriteSeries.filter { open(CategoryKind.SERIES, it.playlistId, it.group) },
                    recentMovies = rows.recentMovies.filter { open(CategoryKind.MOVIE, it.playlistId, it.group) },
                    recentSeries = rows.recentSeries.filter { open(CategoryKind.SERIES, it.playlistId, it.group) },
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

        /** A favourite channel: played in full screen by the TV screen. */
        fun playChannel(channel: Channel) = requests.post(LaunchRequest.PlayChannel(channel.id))
    }
