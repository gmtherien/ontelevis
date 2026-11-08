package app.ontelevis.core.model

/**
 * Parses raw provider (M3U) names into display parts.
 *   "Relax : Night Lights - HDR" -> group "Relax", title "Night Lights", tags [HDR]
 *   "EN | 4K UHD 🏆"              -> group "EN",    title "4K UHD",       tags []
 *   "Live Pay-Per-View 🎟"        -> group null,    title "Live Pay-Per-View"
 */
data class ParsedChannelName(
    val raw: String,
    val title: String,
    val group: String? = null,
    val tags: List<String> = emptyList(),
) {
    /** Up to two initials for the fallback logo tile. */
    val initials: String
        get() {
            val words = title.split(WHITESPACE).filter { it.isNotEmpty() && it[0].isLetterOrDigit() }
            return when {
                words.isEmpty() -> "?"
                words.size == 1 -> words[0].take(2).uppercase()
                else -> "${words[0][0]}${words[1][0]}".uppercase()
            }
        }
}

private val WHITESPACE = Regex("\\s+")
private val EMOJI =
    Regex(
        "[\\x{1F000}-\\x{1FAFF}\\x{2600}-\\x{27BF}\\x{2B00}-\\x{2BFF}\\x{FE0F}\\x{200D}\\x{20E3}]",
    )
private const val QUALITY = "4K|UHD|FHD|HDR|HD|SD|PPV"
private val QUALITY_SUFFIX = Regex("[\\s\\-|]*\\b($QUALITY)\\s*$", RegexOption.IGNORE_CASE)
private val ONLY_QUALITY = Regex("^(?:(?:$QUALITY)\\s*)*$", RegexOption.IGNORE_CASE)
private val GROUP_PREFIX = Regex("^\\s*([^:|]{1,24}?)\\s*[:|]\\s*(.+)$")

fun parseChannelName(raw: String): ParsedChannelName {
    var s = raw.replace(EMOJI, "").replace(WHITESPACE, " ").trim()

    var group: String? = null
    GROUP_PREFIX.find(s)?.let { m ->
        group = m.groupValues[1].trim()
        s = m.groupValues[2].trim()
    }

    val tags = ArrayDeque<String>()
    while (true) {
        val m = QUALITY_SUFFIX.find(s) ?: break
        val rest = s.substring(0, m.range.first).trim()
        if (ONLY_QUALITY.matches(rest)) break // never empty the title: "4K UHD" stays
        tags.addFirst(m.groupValues[1].uppercase())
        s = rest
    }

    return ParsedChannelName(raw = raw, title = s, group = group, tags = tags.toList())
}
