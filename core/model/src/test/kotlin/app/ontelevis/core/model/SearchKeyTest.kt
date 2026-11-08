package app.ontelevis.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchKeyTest {
    @Test
    fun `accents and case are removed`() {
        assertEquals("quebec ete", searchKeyOf("Québec  Été"))
        assertEquals("4k: amelie", searchKeyOf(" 4K: Amélie "))
    }

    @Test
    fun `plain text is unchanged apart from case`() {
        assertEquals("the matrix (1999)", searchKeyOf("The Matrix (1999)"))
    }
}
