package app.ontelevis.core.parser.m3u

import java.io.Reader

/** One channel entry read from an M3U / M3U8 playlist. */
data class M3uEntry(
    val name: String,
    val url: String,
    val attributes: Map<String, String> = emptyMap(),
    val group: String? = null,
) {
    val tvgId: String? get() = attributes["tvg-id"]?.ifBlank { null }
    val tvgName: String? get() = attributes["tvg-name"]?.ifBlank { null }
    val logo: String? get() = attributes["tvg-logo"]?.ifBlank { null }
}

/**
 * Streaming M3U parser: reads line by line and never holds the whole playlist in memory.
 * Playlists with hundreds of thousands of entries must stay cheap to read.
 */
object M3uParser {
    private val attributeRegex = Regex("""([A-Za-z0-9_-]+)="([^"]*)"""")

    fun parse(
        reader: Reader,
        onHeader: (Map<String, String>) -> Unit = {},
    ): Sequence<M3uEntry> = parse(reader.buffered().lineSequence(), onHeader)

    /** The guide link a playlist announces in its `#EXTM3U` line, if any. */
    fun epgUrlOf(header: Map<String, String>): String? =
        (header["url-tvg"] ?: header["x-tvg-url"])
            ?.split(',')
            ?.map { it.trim() }
            ?.firstOrNull { it.startsWith("http", ignoreCase = true) }

    fun parse(
        lines: Sequence<String>,
        onHeader: (Map<String, String>) -> Unit = {},
    ): Sequence<M3uEntry> =
        sequence {
            var pendingInfo: String? = null
            var pendingGroup: String? = null
            for (raw in lines) {
                val line = raw.trim().removePrefix("\uFEFF")
                when {
                    line.isEmpty() -> {}

                    line.startsWith("#EXTM3U", ignoreCase = true) -> {
                        onHeader(
                            attributeRegex.findAll(line).associate {
                                it.groupValues[1].lowercase() to
                                    it.groupValues[2]
                            },
                        )
                    }

                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        pendingInfo = line
                    }

                    line.startsWith("#EXTGRP:", ignoreCase = true) -> {
                        pendingGroup = line.substringAfter(':').trim().ifEmpty { null }
                    }

                    // Other tags (#EXTM3U, #EXTVLCOPT...) are not used yet.
                    line.startsWith("#") -> {}

                    else -> {
                        val info = pendingInfo
                        if (info != null) yield(entryOf(info, line, pendingGroup))
                        pendingInfo = null
                        pendingGroup = null
                    }
                }
            }
        }

    private fun entryOf(
        info: String,
        url: String,
        extGroup: String?,
    ): M3uEntry {
        val commaIndex = displayNameComma(info)
        val header = if (commaIndex >= 0) info.substring(0, commaIndex) else info
        val name = if (commaIndex >= 0) info.substring(commaIndex + 1).trim() else ""
        val attributes = attributeRegex.findAll(header).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        val group = attributes["group-title"]?.ifBlank { null } ?: extGroup
        return M3uEntry(
            name = name.ifEmpty { attributes["tvg-name"].orEmpty() },
            url = url,
            attributes = attributes,
            group = group,
        )
    }

    /** First comma outside a quoted attribute value: what follows it is the channel name. */
    private fun displayNameComma(info: String): Int {
        var quoted = false
        info.forEachIndexed { index, c ->
            when {
                c == '"' -> quoted = !quoted
                c == ',' && !quoted -> return index
            }
        }
        return -1
    }
}
