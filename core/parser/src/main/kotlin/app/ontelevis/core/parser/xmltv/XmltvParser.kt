package app.ontelevis.core.parser.xmltv

import org.xmlpull.v1.XmlPullParser
import java.io.BufferedInputStream
import java.io.InputStream
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.zip.GZIPInputStream

/** One `<programme>` of an XMLTV guide. Times are epoch milliseconds. */
data class XmltvProgramme(
    val channel: String,
    val start: Long,
    val stop: Long,
    val title: String,
    val description: String?,
)

/**
 * Streaming XMLTV reader: hands each programme to [onProgramme] as soon as it is read, so a guide
 * of several hundred MB never sits in memory. Gzip files (`.xml.gz`) are detected by their header.
 *
 * [newParser] creates the pull parser: `android.util.Xml.newPullParser()` on Android, kxml2 in tests.
 */
class XmltvParser(
    private val newParser: () -> XmlPullParser,
) {
    fun parse(
        input: InputStream,
        onProgramme: (XmltvProgramme) -> Unit,
    ) {
        val parser = newParser()
        parser.setInput(gunzipIfNeeded(input), null)
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "programme") {
                readProgramme(parser)?.let(onProgramme)
            }
            event = parser.next()
        }
    }

    private fun readProgramme(parser: XmlPullParser): XmltvProgramme? {
        val channel = parser.getAttributeValue(null, "channel")
        val start = XmltvTime.parse(parser.getAttributeValue(null, "start"))
        val stop = XmltvTime.parse(parser.getAttributeValue(null, "stop"))
        var title: String? = null
        var description: String? = null
        val depth = parser.depth
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.depth == depth) break
            if (event != XmlPullParser.START_TAG) continue
            when (parser.name) {
                // Several titles in different languages: keep the first one.
                "title" -> if (title == null) title = parser.nextText().trim() else parser.nextText()

                "desc" -> if (description == null) description = parser.nextText().trim() else parser.nextText()
            }
        }
        if (channel.isNullOrBlank() || start == null || stop == null || stop <= start) return null
        return XmltvProgramme(channel, start, stop, title.orEmpty(), description?.ifEmpty { null })
    }

    private fun gunzipIfNeeded(input: InputStream): InputStream {
        val buffered = BufferedInputStream(input, BUFFER)
        buffered.mark(2)
        val b0 = buffered.read()
        val b1 = buffered.read()
        buffered.reset()
        return if (b0 == 0x1f &&
            b1 == 0x8b
        ) {
            BufferedInputStream(GZIPInputStream(buffered, BUFFER), BUFFER)
        } else {
            buffered
        }
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}

/** XMLTV dates: `20261108120000 +0100`, offset optional (UTC then), seconds optional. */
object XmltvTime {
    fun parse(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val trimmed = value.trim()
        val digits = trimmed.takeWhile { it.isDigit() }
        if (digits.length < 12) return null
        val padded = digits.take(14).padEnd(14, '0')
        val offset =
            trimmed
                .substring(digits.length)
                .trim()
                .takeIf { it.length >= 5 && (it[0] == '+' || it[0] == '-') }
                ?.let { runCatching { ZoneOffset.of("${it.take(3)}:${it.substring(3, 5)}") }.getOrNull() }
                ?: ZoneOffset.UTC
        return runCatching {
            LocalDateTime
                .of(
                    padded.substring(0, 4).toInt(),
                    padded.substring(4, 6).toInt(),
                    padded.substring(6, 8).toInt(),
                    padded.substring(8, 10).toInt(),
                    padded.substring(10, 12).toInt(),
                    padded.substring(12, 14).toInt(),
                ).toInstant(offset)
                .toEpochMilli()
        }.getOrNull()
    }
}
