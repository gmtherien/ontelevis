package app.ontelevis.feature.vod

import kotlinx.serialization.Serializable

/** Movies grid. */
@Serializable data object MoviesRoute

/** Series grid. */
@Serializable data object SeriesListRoute

@Serializable data class MovieRoute(
    val id: String,
)

@Serializable data class SeriesRoute(
    val id: String,
)

/**
 * Plays a movie or an episode ([contentId]); [fromStart] ignores the resume point.
 * With [catchupChannelId]: plays that live channel's recording from [catchupStart] to [catchupStop]
 * (catch-up, epoch millis), titled [catchupTitle]; no resume point is kept for it.
 */
@Serializable data class VodPlayerRoute(
    val contentId: String,
    val fromStart: Boolean = false,
    val catchupChannelId: String? = null,
    val catchupStart: Long = 0,
    val catchupStop: Long = 0,
    val catchupTitle: String? = null,
)
