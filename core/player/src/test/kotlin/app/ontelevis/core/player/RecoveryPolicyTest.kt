package app.ontelevis.core.player

import app.ontelevis.core.player.RecoveryAction.GiveUp
import app.ontelevis.core.player.RecoveryAction.Restart
import app.ontelevis.core.player.RecoveryAction.RetryAsHls
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RecoveryPolicyTest {
    private val policy = RecoveryPolicy()

    @Test
    fun `404 gives up at once`() {
        assertEquals(GiveUp(PlaybackError.NOT_FOUND), policy.onFailure(FailureKind.GONE, alreadyHls = false, nowMs = 0))
    }

    @Test
    fun `unreadable ts is retried once as HLS, then given up`() {
        assertEquals(RetryAsHls, policy.onFailure(FailureKind.FORMAT, alreadyHls = false, nowMs = 0))
        assertEquals(
            GiveUp(PlaybackError.UNSUPPORTED),
            policy.onFailure(FailureKind.FORMAT, alreadyHls = true, nowMs = 1),
        )
    }

    @Test
    fun `three failures without playing give up, with growing delays before`() {
        assertEquals(Restart(1_000), policy.onFailure(FailureKind.OTHER, false, 0))
        assertEquals(Restart(2_000), policy.onFailure(FailureKind.OTHER, false, 1_000))
        assertEquals(GiveUp(PlaybackError.UNREACHABLE), policy.onFailure(FailureKind.OTHER, false, 3_000))
    }

    @Test
    fun `playing in between resets the never-started count`() {
        repeat(5) { i ->
            assertEquals(Restart(1_000), policy.onFailure(FailureKind.OTHER, false, i * 20_000L))
            policy.onReady()
        }
    }

    @Test
    fun `more than six restarts in a minute is a loop, even with playing in between`() {
        repeat(6) { i -> assertEquals(Restart(0), policy.onLiveEnded(i * 5_000L)) }
        assertEquals(GiveUp(PlaybackError.RESTART_LOOP), policy.onLiveEnded(31_000))
    }

    @Test
    fun `restarts older than the window are forgotten`() {
        repeat(6) { i -> policy.onLiveEnded(i * 5_000L) }
        assertEquals(Restart(0), policy.onLiveEnded(70_000))
    }

    @Test
    fun `behind live window rejoins the live edge`() {
        assertEquals(RecoveryAction.RejoinLive, policy.onFailure(FailureKind.BEHIND_LIVE_WINDOW, true, 0))
    }

    @Test
    fun `reset forgets the HLS attempt`() {
        policy.onFailure(FailureKind.FORMAT, false, 0)
        policy.reset()
        assertEquals(RetryAsHls, policy.onFailure(FailureKind.FORMAT, false, 1))
    }

    @Test
    fun `a decoder this device lacks gives up at once`() {
        assertEquals(
            GiveUp(PlaybackError.DEVICE_CANNOT_DECODE),
            policy.onFailure(FailureKind.DECODER, alreadyHls = false, nowMs = 0),
        )
    }
}
