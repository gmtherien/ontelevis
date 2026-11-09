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
    /** Xtream account, null for M3U or before the first check. */
    val account: XtreamAccount? = null,
)

/** What an Xtream panel says about the subscription (`user_info`). */
data class XtreamAccount(
    /** Epoch millis, null when the account never expires. */
    val expiresAt: Long?,
    val maxConnections: Int,
    val activeConnections: Int,
    /** "Active", "Expired", "Disabled", "Banned"... as the panel writes it; null if unknown. */
    val status: String?,
    val isTrial: Boolean,
) {
    val isActive: Boolean get() = status == null || status.equals("Active", ignoreCase = true)

    /** Whole days until expiry (0 on the last day, negative once expired), null when it never expires. */
    fun daysLeft(now: Long): Long? =
        expiresAt?.let {
            Math.floorDiv(
                it - now,
                java.util.concurrent.TimeUnit.DAYS
                    .toMillis(1),
            )
        }

    /** True when the user should be warned: expired, not active, or [WARN_DAYS] days or fewer left. */
    fun needsWarning(now: Long): Boolean = !isActive || (daysLeft(now)?.let { it <= WARN_DAYS } ?: false)

    companion object {
        const val WARN_DAYS = 7L
    }
}

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
    /** Hidden by the user: left out of the categories unless they ask to see hidden ones. */
    val hidden: Boolean = false,
) {
    /** One of the user's shortcuts (Favourites, History) rather than a provider category. */
    val shortcut: Shortcut? get() = Shortcut.entries.firstOrNull { it.playlistId == playlistId }

    companion object {
        fun of(
            shortcut: Shortcut,
            channelCount: Int = 0,
        ) = ChannelGroup(shortcut.playlistId, "", channelCount)
    }
}

/** Categories made by the app from the user's own lists. Their negative ids never clash with playlists. */
enum class Shortcut(
    val playlistId: Long,
) {
    FAVORITES(-1),
    HISTORY(-2),
}

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
) {
    /** The user's favourite movies or series rather than a provider category. */
    val isFavorites: Boolean get() = playlistId == FAVORITES_ID

    companion object {
        private const val FAVORITES_ID = -1L

        fun favorites(
            kind: VodKind,
            count: Int,
        ) = VodCategory(FAVORITES_ID, kind, "", count)
    }
}

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

/** A started movie or episode ("Continue watching"). */
data class ContinueItem(
    /** Movie or episode id (what to play). */
    val contentId: String,
    /** Movie name, or series name for an episode. */
    val title: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    /** Series of the episode, null for a movie. */
    val seriesId: String?,
    val season: Int,
    val episode: Int,
    val episodeTitle: String?,
    val positionMs: Long,
    val durationMs: Long,
    /** Epoch millis it was last watched. */
    val updatedAt: Long,
    val playlistId: Long,
    val group: String,
) {
    val progress: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
}

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
