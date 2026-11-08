package app.ontelevis.core.model

/** Where a playlist comes from. The app ships no source of its own. */
sealed interface PlaylistSource {
    data class M3u(
        val url: String,
        val epgUrl: String? = null,
    ) : PlaylistSource

    data class Xtream(
        val server: String,
        val username: String,
        val password: String,
    ) : PlaylistSource
}

data class Playlist(
    val id: Long,
    val name: String,
    val source: PlaylistSource,
    val enabled: Boolean = true,
    val channelCount: Int = 0,
    /** Epoch millis of the last successful refresh, null if never loaded. */
    val lastRefreshAt: Long? = null,
    /** Why the last refresh failed, null if it worked. */
    val lastError: RefreshError? = null,
    val programmeCount: Int = 0,
    val lastEpgAt: Long? = null,
    /** Why the last guide refresh failed, null if it worked. */
    val epgError: RefreshError? = null,
    val movieCount: Int = 0,
    val seriesCount: Int = 0,
    /** Epoch millis movies and series were last loaded, null if never (still loading). */
    val lastVodAt: Long? = null,
)

/** Reasons a playlist refresh can fail, shown to the user in plain words. */
enum class RefreshError {
    /** No connection, DNS failure, timeout. */
    NETWORK,

    /** The server answered with an HTTP error. */
    HTTP,

    /** Xtream login refused (wrong user name or password, expired account). */
    AUTH,

    /** The reply could not be read as a playlist. */
    FORMAT,

    /** The playlist was read but contains no live channel. */
    EMPTY,
}

data class ChannelGroup(
    val playlistId: Long,
    /** Provider group name; empty for channels without a group. */
    val name: String,
    val channelCount: Int,
)

data class Channel(
    /** Unique inside the app: `playlistId::rawId`. */
    val id: String,
    val playlistId: Long,
    /** Id given by the provider (Xtream stream id or M3U tvg-id / name). */
    val rawId: String,
    val number: Int,
    val name: String,
    val streamUrl: String,
    val group: String,
    val logoUrl: String?,
    /** Key used to look up guide data. */
    val epgId: String?,
    val catchupDays: Int = 0,
)

data class Programme(
    val channelEpgId: String,
    val startMillis: Long,
    val stopMillis: Long,
    val title: String,
    val description: String? = null,
)

/** Movies or series: the two kinds of on-demand content. */
enum class VodKind { MOVIE, SERIES }

data class VodCategory(
    val playlistId: Long,
    val kind: VodKind,
    /** Provider category name; empty for titles without a category. */
    val name: String,
    val count: Int,
)

data class Movie(
    /** `playlistId::rawId`. */
    val id: String,
    val playlistId: Long,
    val name: String,
    val group: String,
    val streamUrl: String,
    val posterUrl: String?,
    /** Out of 5, 0 when unknown. */
    val rating: Double,
    val year: Int?,
)

/** Extra details of a movie, fetched when its page opens (Xtream only). */
data class MovieDetails(
    val plot: String?,
    val cast: String?,
    val director: String?,
    val genre: String?,
    val durationSecs: Int,
    val backdropUrl: String?,
)

data class Series(
    /** `playlistId::rawId`. */
    val id: String,
    val playlistId: Long,
    val name: String,
    val group: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val genre: String?,
    val year: Int?,
    val rating: Double,
)

data class Episode(
    /** `playlistId::rawId`. */
    val id: String,
    val seriesId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val streamUrl: String,
    val plot: String?,
    val imageUrl: String?,
    val durationSecs: Int,
)

/** Where the user stopped in a movie or an episode. */
data class WatchProgress(
    /** Movie or episode id. */
    val contentId: String,
    val positionMs: Long,
    val durationMs: Long,
) {
    /** Watched to the end (credits may still run): starts over next time. */
    val finished: Boolean get() = durationMs > 0 && positionMs >= durationMs * 95 / 100
}
