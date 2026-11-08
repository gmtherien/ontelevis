package app.ontelevis.core.parser

import app.ontelevis.core.parser.m3u.M3uEntry
import app.ontelevis.core.parser.xtream.XtreamCategory
import app.ontelevis.core.parser.xtream.XtreamLiveStream
import app.ontelevis.core.parser.xtream.XtreamUrls

/** A live channel read from a provider, before it is stored. */
data class ImportedChannel(
    /** Stable inside one playlist, so favourites and history survive a refresh. */
    val rawId: String,
    val number: Int,
    val name: String,
    /** Provider group; empty when the provider gives none. */
    val group: String,
    val streamUrl: String,
    val logoUrl: String?,
    val epgId: String?,
    val catchupDays: Int,
)

object ChannelImport {
    /**
     * M3U has no real channel id. The key is the tvg-id, else the name; copies that share it
     * (ESPN HD / FHD with one tvg-id) get `#2`, `#3`... in playlist order.
     */
    fun fromM3u(entries: Sequence<M3uEntry>): Sequence<ImportedChannel> {
        val seen = HashMap<String, Int>()
        var position = 0
        return entries.mapNotNull { entry ->
            if (entry.url.isBlank()) return@mapNotNull null
            position++
            val base = (entry.tvgId ?: entry.name.ifBlank { entry.url }).lowercase()
            val copies = seen.merge(base, 1, Int::plus) ?: 1
            ImportedChannel(
                rawId = if (copies == 1) base else "$base#$copies",
                number = entry.attributes["tvg-chno"]?.trim()?.toIntOrNull() ?: position,
                name = entry.name.ifBlank { entry.tvgName ?: entry.url },
                group = entry.group.orEmpty(),
                streamUrl = entry.url,
                logoUrl = entry.logo,
                epgId = entry.tvgId,
                catchupDays = entry.attributes["catchup-days"]?.trim()?.toIntOrNull() ?: 0,
            )
        }
    }

    fun fromXtream(
        streams: Sequence<XtreamLiveStream>,
        categories: List<XtreamCategory>,
        urls: XtreamUrls,
    ): Sequence<ImportedChannel> {
        val categoryNames = categories.mapNotNull { c -> c.categoryId?.let { it to c.categoryName.orEmpty() } }.toMap()
        var position = 0
        return streams.mapNotNull { stream ->
            val id = stream.streamId ?: return@mapNotNull null
            position++
            ImportedChannel(
                rawId = id,
                number = stream.num.takeIf { it > 0 } ?: position,
                name = stream.name ?: id,
                group = stream.categoryId?.let(categoryNames::get).orEmpty(),
                streamUrl = urls.liveStream(id),
                logoUrl = stream.streamIcon,
                epgId = stream.epgChannelId,
                catchupDays = if (stream.tvArchive == 1) stream.tvArchiveDuration else 0,
            )
        }
    }
}
