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
}
