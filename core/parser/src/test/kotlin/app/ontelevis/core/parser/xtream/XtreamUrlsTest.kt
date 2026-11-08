package app.ontelevis.core.parser.xtream

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class XtreamUrlsTest {
    @Test
    fun `normalize adds scheme and strips trailing slash and player_api`() {
        assertEquals("http://host.tv:8080", XtreamUrls.normalize("host.tv:8080/"))
        assertEquals("https://host.tv", XtreamUrls.normalize(" https://host.tv/player_api.php "))
    }

    @Test
    fun `builds player api and live urls with encoded credentials`() {
        val urls = XtreamUrls("http://host.tv", "user name", "p&ss")

        assertEquals(
            "http://host.tv/player_api.php?username=user%20name&password=p%26ss&action=get_live_streams",
            urls.playerApi("get_live_streams"),
        )
        assertEquals("http://host.tv/live/user%20name/p%26ss/42.ts", urls.liveStream("42"))
    }

    @Test
    fun `timeshift url writes the start in the panel time zone`() {
        val urls = XtreamUrls("http://example.com:8080", "user", "p@ss")
        val start = java.time.Instant.parse("2026-11-08T12:00:00Z")
        assertEquals(
            "http://example.com:8080/timeshift/user/p%40ss/30/2026-11-08:12-00/42.ts",
            urls.timeshift("42", start, 30, XtreamUrls.zoneOf("UTC")),
        )
        assertEquals(
            "http://example.com:8080/timeshift/user/p%40ss/90/2026-11-08:13-00/42.ts",
            urls.timeshift("42", start, 90, XtreamUrls.zoneOf("Europe/Paris")),
        )
    }

    @Test
    fun `unknown or missing time zones fall back to UTC`() {
        assertEquals(java.time.ZoneOffset.UTC, XtreamUrls.zoneOf(null))
        assertEquals(java.time.ZoneOffset.UTC, XtreamUrls.zoneOf("Not/AZone"))
    }
}
