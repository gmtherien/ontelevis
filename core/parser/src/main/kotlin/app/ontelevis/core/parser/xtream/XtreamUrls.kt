package app.ontelevis.core.parser.xtream

import java.net.URLEncoder

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

    fun xmltv(): String = "$baseUrl/xmltv.php?username=${encode(username)}&password=${encode(password)}"

    companion object {
        fun normalize(server: String): String {
            val trimmed = server.trim().trimEnd('/')
            val withScheme = if (trimmed.contains("://")) trimmed else "http://$trimmed"
            return withScheme.removeSuffix("/player_api.php").trimEnd('/')
        }

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8").replace("+", "%20")
    }
}
