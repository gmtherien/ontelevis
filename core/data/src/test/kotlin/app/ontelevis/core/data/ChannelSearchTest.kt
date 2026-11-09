package app.ontelevis.core.data

import app.ontelevis.core.data.db.channelSearchKey
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ChannelSearchTest {
    private val names =
        listOf(
            "1" to "CA | RDS 2 HD",
            "2" to "CA | RDS HD",
            "3" to "CA | RDS Info",
            "4" to "FR: TV5 Monde",
            "5" to "CA | Cards TV",
        )

    private fun find(
        query: String,
        favorites: Set<String> = emptySet(),
        recent: List<String> = emptyList(),
    ) = bestChannelMatches(
        query,
        names,
        keyOf = { channelSearchKey(it.second) },
        idOf = { it.first },
        favorites = favorites,
        recent = recent,
    ).map { it.first }

    @Test
    fun `exact title first, then whole-word prefix, then other matches`() {
        assertEquals(listOf("2", "1", "3", "5"), find("rds"))
    }

    @Test
    fun `quality tags and group prefixes are ignored`() {
        assertEquals("2", find("RDS HD").first())
        assertEquals("4", find("tv5 monde").first())
    }

    @Test
    fun `favourites and recent channels win between equals`() {
        assertEquals(listOf("2", "3", "1", "5"), find("rds", favorites = setOf("3")))
        assertEquals("3", find("rds i").first())
    }
}
