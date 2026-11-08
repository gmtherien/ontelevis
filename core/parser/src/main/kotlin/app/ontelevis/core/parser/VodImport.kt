package app.ontelevis.core.parser

import app.ontelevis.core.parser.m3u.M3uEntry
import app.ontelevis.core.parser.xtream.XtreamCategory
import app.ontelevis.core.parser.xtream.XtreamEpisode
import app.ontelevis.core.parser.xtream.XtreamSeries
import app.ontelevis.core.parser.xtream.XtreamUrls
import app.ontelevis.core.parser.xtream.XtreamVodStream

data class ImportedMovie(
    val rawId: String,
    val name: String,
    val group: String,
    val streamUrl: String,
    val posterUrl: String?,
    /** Out of 5, 0 when unknown. */
    val rating: Double,
    val year: Int?,
    /** Epoch seconds the provider added it, 0 when unknown. */
    val added: Long,
)

data class ImportedSeries(
    val rawId: String,
    val name: String,
    val group: String,
    val posterUrl: String?,
    val backdropUrl: String?,
    val plot: String?,
    val genre: String?,
    val year: Int?,
    val rating: Double,
)

data class ImportedEpisode(
    /** Series [ImportedSeries.rawId] this episode belongs to. */
    val seriesRawId: String,
    val rawId: String,
    val season: Int,
    val episode: Int,
    val title: String,
    val streamUrl: String,
    val plot: String?,
    val imageUrl: String?,
    val durationSecs: Int,
)

private val YEAR = Regex("""\((\d{4})\)""")
private val YEAR_IN_DATE = Regex("""^(\d{4})""")

/** "(1994)" in a title, else the year of a date like "1960-12-09". */
internal fun yearOf(
    name: String?,
    date: String? = null,
): Int? =
    name?.let {
        YEAR
            .find(it)
            ?.groupValues
            ?.get(1)
            ?.toIntOrNull()
    }
        ?: date?.let {
            YEAR_IN_DATE
                .find(it.trim())
                ?.groupValues
                ?.get(1)
                ?.toIntOrNull()
        }

object VodImport {
    fun fromXtreamMovies(
        streams: Sequence<XtreamVodStream>,
        categories: List<XtreamCategory>,
        urls: XtreamUrls,
    ): Sequence<ImportedMovie> {
        val names = categories.mapNotNull { c -> c.categoryId?.let { it to c.categoryName.orEmpty() } }.toMap()
        return streams.mapNotNull { s ->
            val id = s.streamId ?: return@mapNotNull null
            ImportedMovie(
                rawId = id,
                name = s.name ?: id,
                group = s.categoryId?.let(names::get).orEmpty(),
                streamUrl = urls.movie(id, s.extension ?: "mp4"),
                posterUrl = s.streamIcon,
                rating = s.rating5,
                year = yearOf(s.name),
                added = s.added?.toLongOrNull() ?: 0,
            )
        }
    }

    fun fromXtreamSeries(
        series: Sequence<XtreamSeries>,
        categories: List<XtreamCategory>,
    ): Sequence<ImportedSeries> {
        val names = categories.mapNotNull { c -> c.categoryId?.let { it to c.categoryName.orEmpty() } }.toMap()
        return series.mapNotNull { s ->
            val id = s.seriesId ?: return@mapNotNull null
            ImportedSeries(
                rawId = id,
                name = s.name ?: id,
                group = s.categoryId?.let(names::get).orEmpty(),
                posterUrl = s.cover,
                backdropUrl = s.backdrop,
                plot = s.plot,
                genre = s.genre,
                year = yearOf(s.name, s.releaseDate),
                rating = s.rating5,
            )
        }
    }

    fun fromXtreamEpisodes(
        seriesRawId: String,
        episodes: List<XtreamEpisode>,
        urls: XtreamUrls,
    ): List<ImportedEpisode> =
        episodes
            .mapNotNull { e ->
                val id = e.id ?: return@mapNotNull null
                ImportedEpisode(
                    seriesRawId = seriesRawId,
                    rawId = id,
                    season = e.season,
                    episode = e.episodeNum,
                    title = e.title ?: "",
                    streamUrl = urls.episode(id, e.extension ?: "mp4"),
                    plot = e.info?.plot,
                    imageUrl = e.info?.image,
                    durationSecs = e.info?.durationSecs ?: 0,
                )
            }.sortedWith(compareBy({ it.season }, { it.episode }))
}

/** What an M3U entry is: a live channel, a movie, or an episode of a series. */
sealed interface M3uKind {
    data object Live : M3uKind

    data object Movie : M3uKind

    data class Episode(
        val series: String,
        val season: Int,
        val episode: Int,
    ) : M3uKind
}

/**
 * Sorts M3U entries. M3U has no type field, so the signs are, in order: Xtream-style paths
 * (`/movie/`, `/series/`), an `S01E02` marker in the name, a video file extension, then words in
 * the group name. Everything else is live.
 */
object M3uClassifier {
    private val EPISODE = Regex("""^(.*?)[\s._\-|]*\bS(\d{1,2})\s*[._-]?\s*E(\d{1,3})\b""", RegexOption.IGNORE_CASE)
    private val VIDEO_FILE = Regex("""\.(mp4|mkv|avi|m4v|mov|wmv|webm)$""", RegexOption.IGNORE_CASE)
    private val MOVIE_GROUP = Regex("""\b(vod|movies?|films?)\b""", RegexOption.IGNORE_CASE)
    private val SERIES_GROUP = Regex("""\b(series|s[ée]ries?|tv shows?)\b""", RegexOption.IGNORE_CASE)

    fun kind(entry: M3uEntry): M3uKind {
        val path = entry.url.substringBefore('?').lowercase()
        val episode = EPISODE.find(entry.name)
        val episodeKind =
            episode?.let {
                M3uKind.Episode(
                    series = it.groupValues[1].trim().ifEmpty { entry.group.orEmpty() },
                    season = it.groupValues[2].toInt(),
                    episode = it.groupValues[3].toInt(),
                )
            }
        val group = entry.group.orEmpty()
        return when {
            "/series/" in path -> episodeKind ?: M3uKind.Episode(entry.name, 1, 0)

            "/movie/" in path -> M3uKind.Movie

            episodeKind != null && (
                VIDEO_FILE.containsMatchIn(
                    path,
                ) || SERIES_GROUP.containsMatchIn(group)
            ) -> episodeKind

            VIDEO_FILE.containsMatchIn(path) -> M3uKind.Movie

            SERIES_GROUP.containsMatchIn(group) -> episodeKind ?: M3uKind.Episode(entry.name, 1, 0)

            MOVIE_GROUP.containsMatchIn(group) -> M3uKind.Movie

            else -> M3uKind.Live
        }
    }

    fun movie(entry: M3uEntry): ImportedMovie =
        ImportedMovie(
            rawId = (entry.tvgId ?: entry.url).lowercase(),
            name = entry.name,
            group = entry.group.orEmpty(),
            streamUrl = entry.url,
            posterUrl = entry.logo,
            rating = 0.0,
            year = yearOf(entry.name),
            added = 0,
        )

    fun episode(
        entry: M3uEntry,
        kind: M3uKind.Episode,
        indexInSeries: Int,
    ): ImportedEpisode =
        ImportedEpisode(
            seriesRawId = seriesKey(kind.series),
            rawId = entry.url.lowercase(),
            season = kind.season,
            episode = if (kind.episode > 0) kind.episode else indexInSeries,
            title = entry.name,
            streamUrl = entry.url,
            plot = null,
            imageUrl = entry.logo,
            durationSecs = 0,
        )

    fun seriesKey(name: String): String = name.trim().lowercase()
}
