package app.ontelevis.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChannelNameTest {
    @Test fun groupTitleAndQualityTag() {
        val p = parseChannelName("Relax : Night Lights - HDR")
        assertEquals("Relax", p.group)
        assertEquals("Night Lights", p.title)
        assertEquals(listOf("HDR"), p.tags)
        assertEquals("NL", p.initials)
    }

    @Test fun pipeSeparatorAndEmoji() {
        val p = parseChannelName("EN | 4K UHD 🏆")
        assertEquals("EN", p.group)
        assertEquals("4K UHD", p.title)
        assertTrue(p.tags.isEmpty())
    }

    @Test fun noGroup() {
        val p = parseChannelName("Live Pay-Per-View 🎟️")
        assertNull(p.group)
        assertEquals("Live Pay-Per-View", p.title)
    }

    @Test fun multipleTags() {
        val p = parseChannelName("Sports : Arena 1 FHD HDR")
        assertEquals("Arena 1", p.title)
        assertEquals(listOf("FHD", "HDR"), p.tags)
    }

    @Test fun emojiInsideCategory() {
        assertEquals("Relaxation", parseChannelName("Relaxation 🌴").title)
        assertEquals("24/7 Halloween", parseChannelName("24/7 Halloween 🎃").title)
    }
}
