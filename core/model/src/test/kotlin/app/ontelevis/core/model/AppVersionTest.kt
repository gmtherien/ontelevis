package app.ontelevis.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AppVersionTest {
    private fun v(text: String) = AppVersion.parse(text)!!

    @Test
    fun `tags and version names parse`() {
        assertEquals(AppVersion(1, 0, 0, listOf("beta", "2")), v("v1.0.0-beta.2"))
        assertEquals(AppVersion(1, 2, 3), v("1.2.3"))
        assertNull(AppVersion.parse("1.2"))
        assertNull(AppVersion.parse("latest"))
        assertNull(AppVersion.parse(null))
    }

    @Test
    fun `semantic order`() {
        val sorted =
            listOf("1.0.1", "1.0.0", "1.0.0-beta.10", "1.0.0-beta.2", "0.9.9", "1.0.0-rc.1", "1.0.0-beta")
                .map(::v)
                .sorted()
                .map { it.toString() }
        assertEquals(
            listOf("0.9.9", "1.0.0-beta", "1.0.0-beta.2", "1.0.0-beta.10", "1.0.0-rc.1", "1.0.0", "1.0.1"),
            sorted,
        )
    }

    @Test
    fun `pre-release flag`() {
        assertTrue(v("1.0.0-beta.3").isPreRelease)
        assertTrue(!v("1.0.0").isPreRelease)
    }
}
