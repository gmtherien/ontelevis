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
)

data class Channel(
    /** Unique inside the app: `playlistId::rawId`. */
    val id: String,
    val playlistId: Long,
    /** Id given by the provider (Xtream stream id or M3U tvg-id / position). */
    val rawId: String,
    val name: String,
    val streamUrl: String,
    val group: String?,
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
