package app.ontelevis.core.parser.xtream

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class XtreamJsonTest {
    private fun String.stream() = byteInputStream()

    @Test
    fun `account with numbers sent as strings`() {
        val reply =
            """{"user_info":{"auth":1,"status":"Active","exp_date":"1767225600","max_connections":"2"}}"""

        val info = XtreamJson.decodeAccount(reply.stream()).userInfo!!

        assertEquals(1, info.auth)
        assertEquals("Active", info.status)
        assertEquals("1767225600", info.expDate)
        assertEquals(2, info.maxConnections)
    }

    @Test
    fun `failed login has auth 0`() {
        val info = XtreamJson.decodeAccount("""{"user_info":{"auth":0}}""".stream()).userInfo!!
        assertEquals(0, info.auth)
    }

    @Test
    fun `categories accept numeric ids`() {
        val reply =
            """[{"category_id":"1","category_name":"News"},{"category_id":2,"category_name":"Sports"}]"""

        val categories = XtreamJson.decodeCategories(reply.stream())

        assertEquals(listOf("1" to "News", "2" to "Sports"), categories.map { it.categoryId to it.categoryName })
    }

    @Test
    fun `live streams tolerate empty strings, nulls and unknown keys`() {
        val reply =
            """
            [
              {"num":1,"name":"News HD","stream_type":"live","stream_id":101,"stream_icon":"http://x/n.png",
               "epg_channel_id":"news.ca","category_id":"1","tv_archive":1,"tv_archive_duration":"7"},
              {"num":"2","name":"Sports","stream_id":"102","stream_icon":"","epg_channel_id":null,
               "category_id":null,"tv_archive":"","tv_archive_duration":null,"custom_sid":""}
            ]
            """.trimIndent()

        val streams = XtreamJson.decodeLiveStreams(reply.stream()).toList()

        assertEquals(2, streams.size)
        assertEquals("101", streams[0].streamId)
        assertEquals(7, streams[0].tvArchiveDuration)
        assertEquals(1, streams[0].tvArchive)
        assertEquals(2, streams[1].num)
        assertNull(streams[1].streamIcon)
        assertNull(streams[1].epgChannelId)
        assertNull(streams[1].categoryId)
        assertEquals(0, streams[1].tvArchive)
    }
}
