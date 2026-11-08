package app.ontelevis.core.parser

import app.ontelevis.core.parser.m3u.M3uParser
import app.ontelevis.core.parser.xtream.XtreamCategory
import app.ontelevis.core.parser.xtream.XtreamLiveStream
import app.ontelevis.core.parser.xtream.XtreamUrls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChannelImportTest {
    @Test
    fun `m3u copies sharing a tvg-id get unique ids`() {
        val playlist =
            """
            #EXTINF:-1 tvg-id="espn.us" group-title="Sports",ESPN HD
            http://x/1.ts
            #EXTINF:-1 tvg-id="espn.us" group-title="Sports",ESPN FHD
            http://x/2.ts
            #EXTINF:-1 tvg-chno="7" catchup-days="3",No Id
            http://x/3.ts
            """.trimIndent()

        val channels = ChannelImport.fromM3u(M3uParser.parse(playlist.lineSequence())).toList()

        assertEquals(listOf("espn.us", "espn.us#2", "no id"), channels.map { it.rawId })
        assertEquals(listOf(1, 2, 7), channels.map { it.number })
        assertEquals(listOf("Sports", "Sports", ""), channels.map { it.group })
        assertEquals(3, channels[2].catchupDays)
        assertEquals("espn.us", channels[1].epgId)
    }

    @Test
    fun `xtream streams get category names, stream urls and catch-up`() {
        val urls = XtreamUrls("http://host", "u", "p")
        val streams =
            sequenceOf(
                XtreamLiveStream(
                    num = 5,
                    name = "News",
                    streamId = "101",
                    categoryId = "1",
                    tvArchive = 1,
                    tvArchiveDuration = 7,
                ),
                XtreamLiveStream(name = "Orphan", streamId = "102", categoryId = "9"),
                XtreamLiveStream(name = "No id"),
            )

        val channels = ChannelImport.fromXtream(streams, listOf(XtreamCategory("1", "Canada")), urls).toList()

        assertEquals(2, channels.size)
        assertEquals("Canada", channels[0].group)
        assertEquals(5, channels[0].number)
        assertEquals(7, channels[0].catchupDays)
        assertEquals("http://host/live/u/p/101.ts", channels[0].streamUrl)
        assertEquals("", channels[1].group)
        assertEquals(2, channels[1].number)
        assertEquals(0, channels[1].catchupDays)
    }
}
