package app.ontelevis.core.parser.m3u

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class M3uParserTest {
    @Test
    fun `reads attributes, group and name`() {
        val playlist =
            """
            #EXTM3U url-tvg="http://example.com/epg.xml"
            #EXTINF:-1 tvg-id="news.ca" tvg-name="News HD" tvg-logo="http://example.com/n.png" group-title="Canada",News HD
            http://example.com/live/1.ts
            """.trimIndent()

        val entries = M3uParser.parse(playlist.lineSequence()).toList()

        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("News HD", entry.name)
        assertEquals("http://example.com/live/1.ts", entry.url)
        assertEquals("news.ca", entry.tvgId)
        assertEquals("http://example.com/n.png", entry.logo)
        assertEquals("Canada", entry.group)
    }

    @Test
    fun `comma inside a quoted attribute does not cut the name`() {
        val playlist =
            """
            #EXTINF:-1 group-title="Sports, US",ESPN
            http://example.com/espn.m3u8
            """.trimIndent()

        val entry = M3uParser.parse(playlist.lineSequence()).single()

        assertEquals("ESPN", entry.name)
        assertEquals("Sports, US", entry.group)
    }

    @Test
    fun `EXTGRP is used when group-title is missing, and other tags are skipped`() {
        val playlist =
            """
            #EXTM3U
            #EXTINF:-1,Movie channel
            #EXTGRP:Movies
            #EXTVLCOPT:http-user-agent=Test
            http://example.com/movies.ts
            #EXTINF:-1,No group
            http://example.com/other.ts
            """.trimIndent()

        val entries = M3uParser.parse(playlist.lineSequence()).toList()

        assertEquals(listOf("Movie channel", "No group"), entries.map { it.name })
        assertEquals("Movies", entries[0].group)
        assertNull(entries[1].group)
        assertNull(entries[1].tvgId)
    }

    @Test
    fun `URL without EXTINF is ignored and BOM is stripped`() {
        val playlist = "\uFEFF#EXTM3U\nhttp://example.com/orphan.ts\n#EXTINF:-1,Kept\nhttp://example.com/kept.ts\n"

        val entries = M3uParser.parse(playlist.reader()).toList()

        assertEquals(listOf("Kept"), entries.map { it.name })
    }

    @Test
    fun `parsing is lazy`() {
        var linesRead = 0
        val lines =
            generateSequence(0) { it + 1 }
                .map { i -> if (i % 2 == 0) "#EXTINF:-1,Channel $i" else "http://example.com/$i.ts" }
                .onEach { linesRead++ }

        val firstThree = M3uParser.parse(lines).take(3).toList()

        assertEquals(3, firstThree.size)
        assertEquals(6, linesRead)
    }

    @Test
    fun `header announces the guide link`() {
        var header = emptyMap<String, String>()
        val playlist =
            "#EXTM3U x-tvg-url=\"https://a.example/epg.xml.gz,https://b.example/epg.xml\"\n" +
                "#EXTINF:-1,A\nhttp://x/a.ts"

        M3uParser.parse(playlist.lineSequence()) { header = it }.toList()

        assertEquals("https://a.example/epg.xml.gz", M3uParser.epgUrlOf(header))
    }
}
