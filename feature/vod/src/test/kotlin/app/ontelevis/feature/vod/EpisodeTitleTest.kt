package app.ontelevis.feature.vod

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EpisodeTitleTest {
    @Test
    fun `series name and SxxExx marker are removed`() {
        assertEquals(
            "Fri Dec 09 1960",
            cleanEpisodeTitle("Coronation Street - S01E01 - Fri Dec 09 1960", "Coronation Street (1960)"),
        )
    }

    @Test
    fun `a title that is only the marker falls back to the raw title`() {
        assertEquals("Show - S02E03", cleanEpisodeTitle("Show - S02E03", "Show"))
    }

    @Test
    fun `plain titles are kept`() {
        assertEquals("The Pilot", cleanEpisodeTitle("The Pilot", "Other Show"))
    }

    @Test
    fun `durations`() {
        assertEquals("1 h 05", formatDuration(65 * 60_000L))
        assertEquals("42 min", formatDuration(42 * 60_000L))
    }

    @Test
    fun `alphabetical order skips provider prefixes only`() {
        assertEquals("Untold", sortTitle("4K: Untold"))
        assertEquals("Amélie", sortTitle("FR| Amélie"))
        assertEquals("Heat", sortTitle("EN - Heat"))
        assertEquals("ALIEN: Romulus", sortTitle("ALIEN: Romulus"))
        assertEquals("TRON: Legacy", sortTitle("TRON: Legacy"))
    }
}
