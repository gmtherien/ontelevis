package app.ontelevis.core.parser.xmltv

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.kxml2.io.KXmlParser
import java.io.ByteArrayOutputStream
import java.time.Instant
import java.util.zip.GZIPOutputStream

class XmltvParserTest {
    private val parser = XmltvParser { KXmlParser() }

    private val guide =
        """
        <?xml version="1.0" encoding="UTF-8"?>
        <tv generator-info-name="test">
          <channel id="news.ca"><display-name>News</display-name></channel>
          <programme start="20261108100000 +0000" stop="20261108110000 +0000" channel="news.ca">
            <title lang="en">Morning News</title>
            <title lang="fr">Nouvelles du matin</title>
            <desc lang="en">Headlines &amp; weather.</desc>
            <category>News</category>
          </programme>
          <programme start="20261108110000 -0500" stop="20261108120000 -0500" channel="news.ca">
            <title>Late Show</title>
          </programme>
          <programme start="bad" stop="20261108120000" channel="news.ca"><title>Broken</title></programme>
        </tv>
        """.trimIndent()

    private fun parse(bytes: ByteArray): List<XmltvProgramme> {
        val out = mutableListOf<XmltvProgramme>()
        parser.parse(bytes.inputStream()) { out += it }
        return out
    }

    @Test
    fun `reads programmes, first title, description and offsets`() {
        val programmes = parse(guide.toByteArray())

        assertEquals(2, programmes.size)
        val first = programmes[0]
        assertEquals("news.ca", first.channel)
        assertEquals("Morning News", first.title)
        assertEquals("Headlines & weather.", first.description)
        assertEquals(Instant.parse("2026-11-08T10:00:00Z").toEpochMilli(), first.start)
        // 11:00 at -05:00 is 16:00 UTC.
        assertEquals(Instant.parse("2026-11-08T16:00:00Z").toEpochMilli(), programmes[1].start)
        assertNull(programmes[1].description)
    }

    @Test
    fun `gzip guides are detected`() {
        val zipped = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(guide.toByteArray()) } }

        assertEquals(2, parse(zipped.toByteArray()).size)
    }

    @Test
    fun `time formats`() {
        assertEquals(Instant.parse("2026-11-08T12:00:00Z").toEpochMilli(), XmltvTime.parse("20261108120000"))
        assertEquals(Instant.parse("2026-11-08T11:00:00Z").toEpochMilli(), XmltvTime.parse("202611081200 +0100"))
        assertNull(XmltvTime.parse("2026"))
        assertNull(XmltvTime.parse(null))
    }

    @Test
    fun `a raw ampersand does not lose the guide in relaxed mode`() {
        val relaxed =
            XmltvParser { KXmlParser().apply { setFeature("http://xmlpull.org/v1/doc/features.html#relaxed", true) } }
        val broken =
            """<tv><programme start="20261108100000" stop="20261108110000" channel="a"><title>Health & Life</title></programme></tv>"""
        val out = mutableListOf<XmltvProgramme>()

        relaxed.parse(broken.byteInputStream()) { out += it }

        assertEquals(listOf("Health & Life"), out.map { it.title })
    }
}
