package app.ontelevis.core.model.epg

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import java.time.Instant

class EpgLayoutTest {
    private fun t(hhmm: String) = Instant.parse("2026-11-07T$hhmm:00Z")

    @Test fun windowStartsOneSlotBeforeCurrentSlot() {
        val w = EpgWindow.around(t("10:50"))
        assertEquals(t("10:00"), w.start)
        assertEquals(t("12:00"), w.end)
        assertEquals(listOf(t("10:00"), t("10:30"), t("11:00"), t("11:30")), w.ticks())
    }

    @Test fun nowLineFraction() {
        val w = EpgWindow.around(t("10:50"))
        assertEquals(50f / 120f, w.fractionOf(t("10:50")), 0.0001f)
    }

    @Test fun spanIsClampedAndHiddenOutside() {
        val w = EpgWindow(t("10:00"))
        assertEquals(0f to 0.25f, w.spanOf(t("09:30"), t("10:30")))
        assertNull(w.spanOf(t("08:00"), t("09:00")))
        assertNull(w.spanOf(t("12:00"), t("13:00")))
    }

    @Test fun stateProgressAndMinutesLeft() {
        val now = t("10:50")
        assertEquals(ProgramState.PAST, programState(t("09:30"), t("10:30"), now))
        assertEquals(ProgramState.CURRENT, programState(t("10:30"), t("11:30"), now))
        assertEquals(ProgramState.UPCOMING, programState(t("11:30"), t("12:30"), now))
        assertEquals(1f / 3f, progressOf(t("10:30"), t("11:30"), now), 0.0001f)
        assertEquals(40L, minutesLeft(t("11:30"), now))
    }
}
