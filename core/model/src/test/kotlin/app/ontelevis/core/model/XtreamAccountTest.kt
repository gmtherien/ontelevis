package app.ontelevis.core.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class XtreamAccountTest {
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_800_000_000_000L

    private fun account(
        expiresAt: Long?,
        status: String? = "Active",
    ) = XtreamAccount(expiresAt, maxConnections = 1, activeConnections = 0, status = status, isTrial = false)

    @Test
    fun `days left counts whole days`() {
        assertEquals(10L, account(now + 10 * day + 1000).daysLeft(now))
        assertEquals(0L, account(now + 3_600_000).daysLeft(now))
        assertEquals(-1L, account(now - 1000).daysLeft(now))
        assertNull(account(null).daysLeft(now))
    }

    @Test
    fun `warning a week before expiry, when expired or not active`() {
        assertFalse(account(now + 30 * day).needsWarning(now))
        assertTrue(account(now + 7 * day).needsWarning(now))
        assertTrue(account(now - day).needsWarning(now))
        assertFalse(account(null).needsWarning(now))
        assertTrue(account(null, status = "Disabled").needsWarning(now))
        assertFalse(account(null, status = "active").needsWarning(now))
    }
}
