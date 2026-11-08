package app.ontelevis.core.parser.xtream

import java.net.URLEncoder
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Builds the URLs of an Xtream Codes server. Credentials never end up in logs from here. */
class XtreamUrls(
    server: String,
    private val username: String,
    private val password: String,
) {
    val baseUrl: String = normalize(server)

    fun playerApi(action: String? = null): String {
        val query =
            buildString {
                append("username=").append(encode(username))
                append("&password=").append(encode(password))
                if (action != null) append("&action=").append(encode(action))
            }
        return "$baseUrl/player_api.php?$query"
    }

    fun liveStream(
        streamId: String,
        extension: String = "ts",
    ): String = "$baseUrl/live/${encode(username)}/${encode(password)}/$streamId.$extension"

    fun movie(
        streamId: String,
        extension: String,
    ): String = "$baseUrl/movie/${encode(username)}/${encode(password)}/$streamId.$extension"

    fun episode(
        episodeId: String,
        extension: String,
    ): String = "$baseUrl/series/${encode(username)}/${encode(password)}/$episodeId.$extension"

    /**
     * Catch-up recording of a live stream: [durationMinutes] from [start], written in the panel's
     * time zone ([zone], from `server_info.timezone`) as `yyyy-MM-dd:HH-mm`.
     */
    fun timeshift(
        streamId: String,
        start: Instant,
        durationMinutes: Int,
        zone: ZoneId,
    ): String {
        val at = TIMESHIFT_FORMAT.format(start.atZone(zone))
        return "$baseUrl/timeshift/${encode(username)}/${encode(password)}/$durationMinutes/$at/$streamId.ts"
    }

    fun xmltv(): String = "$baseUrl/xmltv.php?username=${encode(username)}&password=${encode(password)}"

    companion object {
        private val TIMESHIFT_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd:HH-mm", Locale.ROOT)

        /** The panel's time zone, UTC when missing or unknown. */
        fun zoneOf(timezone: String?): ZoneId =
            timezone?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: ZoneOffset.UTC

        fun normalize(server: String): String {
            val trimmed = server.trim().trimEnd('/')
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return withScheme.removeSuffix("/player_api.php").trimEnd('/')
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
