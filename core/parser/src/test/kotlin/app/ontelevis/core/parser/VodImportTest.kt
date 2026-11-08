package app.ontelevis.core.parser

import app.ontelevis.core.parser.m3u.M3uEntry
import app.ontelevis.core.parser.xtream.XtreamCategory
import app.ontelevis.core.parser.xtream.XtreamUrls
import app.ontelevis.core.parser.xtream.XtreamVodJson
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class VodImportTest {
    private val urls = XtreamUrls("http://host", "u", "p")

    @Test
    fun `xtream movies get category, stream url, rating and year`() {
        val reply =
            """[{"num":1,"name":"A Dangerous Place (1994)","stream_id":100922,"stream_icon":"http://img/p.jpg",
               "rating":"5","rating_5based":2.5,"added":"1793082127","category_id":"879","container_extension":"mkv"}]"""

        val movies =
            VodImport
                .fromXtreamMovies(
                    XtreamVodJson.decodeVodStreams(reply.byteInputStream()),
                    listOf(XtreamCategory("879", "Action")),
                    urls,
                ).toList()

        val movie = movies.single()
        assertEquals("Action", movie.group)
        assertEquals("http://host/movie/u/p/100922.mkv", movie.streamUrl)
        assertEquals(2.5, movie.rating)
        assertEquals(1994, movie.year)
        assertEquals(1793082127L, movie.added)
    }

    @Test
    fun `xtream series accept backdrop as an array and dates for the year`() {
        val reply =
            """[{"name":"Coronation Street","series_id":572213,"cover":"http://img/c.jpg","plot":"Soap.",
               "releaseDate":"1960-12-09","rating_5based":"2.5","backdrop_path":["http://img/b.jpg"],"category_id":"919"}]"""

        val series =
            VodImport
                .fromXtreamSeries(
                    XtreamVodJson.decodeSeries(reply.byteInputStream()),
                    emptyList(),
                ).single()

        assertEquals("http://img/b.jpg", series.backdropUrl)
        assertEquals(1960, series.year)
        assertEquals(2.5, series.rating)
    }

    @Test
    fun `episodes come grouped by season and are sorted`() {
        val reply =
            """{"seasons":[],"info":{},"episodes":{
                 "2":[{"id":"20","episode_num":1,"title":"S2E1","container_extension":"mp4","info":{"duration_secs":1500}}],
                 "1":[{"id":"12","episode_num":2,"title":"S1E2","container_extension":"mkv"},
                      {"id":"11","episode_num":1,"title":"S1E1","container_extension":"mkv","info":{"plot":"Pilot"}}]}}"""

        val episodes =
            VodImport.fromXtreamEpisodes(
                "572213",
                XtreamVodJson.decodeEpisodes(reply.byteInputStream()),
                urls,
            )

        assertEquals(listOf("11", "12", "20"), episodes.map { it.rawId })
        assertEquals(listOf(1, 1, 2), episodes.map { it.season })
        assertEquals("http://host/series/u/p/11.mkv", episodes[0].streamUrl)
        assertEquals("Pilot", episodes[0].plot)
        assertEquals(1500, episodes[2].durationSecs)
    }

    private fun entry(
        name: String,
        url: String,
        group: String? = null,
    ) = M3uEntry(name = name, url = url, group = group)

    @Test
    fun `m3u entries are sorted into live, movies and episodes`() {
        assertEquals(M3uKind.Live, M3uClassifier.kind(entry("CNN HD", "http://x/live/u/p/1.ts", "News")))
        assertEquals(M3uKind.Movie, M3uClassifier.kind(entry("Heat (1995)", "http://x/movie/u/p/2.mkv")))
        assertEquals(M3uKind.Movie, M3uClassifier.kind(entry("Some Film", "http://x/files/film.mp4?token=1")))
        assertEquals(M3uKind.Movie, M3uClassifier.kind(entry("Old Film", "http://x/stream/9", "VOD | Classics")))
        assertEquals(
            M3uKind.Episode("Breaking Bad", 2, 5),
            M3uClassifier.kind(entry("Breaking Bad S02 E05", "http://x/series/u/p/3.mkv")),
        )
        assertEquals(
            M3uKind.Episode("The Office", 1, 12),
            M3uClassifier.kind(entry("The Office - S01E12", "http://x/vod/office-s01e12.mp4")),
        )
        // A live channel whose name looks like an episode stays live without other signs.
        assertEquals(M3uKind.Live, M3uClassifier.kind(entry("Sports S1E2 Live", "http://x/live/4.ts", "Sports")))
    }

    @Test
    fun `m3u episodes without numbers are numbered in order`() {
        val kind = M3uClassifier.kind(entry("Pilot", "http://x/dl/a.ts", "Séries | Drama")) as M3uKind.Episode
        val episode =
            M3uClassifier.episode(
                entry("Pilot", "http://x/dl/a.ts", "Séries | Drama"),
                kind,
                indexInSeries = 3,
            )

        assertEquals(3, episode.episode)
        assertEquals(M3uClassifier.seriesKey("Pilot"), episode.seriesRawId)
        assertNull(episode.plot)
    }
}
