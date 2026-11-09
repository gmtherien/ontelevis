package app.ontelevis.core.player

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class TrackLabelTest {
    private val fr = Locale.FRENCH

    @Test
    fun `language name, codec and channels`() {
        assertEquals("Anglais · AC3 5.1", trackLabel(null, "en", "audio/ac3", 6, fr, "Piste 1"))
        assertEquals("Français · AAC stereo", trackLabel(null, "fr", "audio/mp4a-latm", 2, fr, "Piste 1"))
    }

    @Test
    fun `the stream's label is kept and the language added when missing`() {
        assertEquals(
            "Anglais (Commentary) · AAC stereo",
            trackLabel("Commentary", "en", "audio/mp4a-latm", 2, fr, "Piste 1"),
        )
        assertEquals("Français AD", trackLabel("Français AD", "fr", null, 0, fr, "Piste 1"))
    }

    @Test
    fun `unknown language and codec`() {
        assertEquals("Piste 3", trackLabel(null, null, "audio/unknown", 0, fr, "Piste 3"))
        assertEquals("Anglais · CC", trackLabel(null, "en", "application/cea-608", 0, fr, "Piste 1"))
    }
}
