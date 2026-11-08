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

/** Plays a movie or an episode ([contentId]); [fromStart] ignores the resume point. */
@Serializable data class VodPlayerRoute(
    val contentId: String,
    val fromStart: Boolean = false,
)
