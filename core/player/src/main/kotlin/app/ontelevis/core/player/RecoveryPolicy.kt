package app.ontelevis.core.player

/** Why playback stopped for good. Shown to the user in plain words. */
enum class PlaybackError {
    /** HTTP 404 / 410: the channel no longer exists on the server. */
    NOT_FOUND,

    /** The stream format cannot be read, even as HLS. */
    UNSUPPORTED,

    /** The stream never started after several attempts. */
    UNREACHABLE,

    /** Too many restarts in a short time (provider cutting the connection in a loop). */
    RESTART_LOOP,

    /** This device has no decoder for the video (e.g. 4K HEVC or Dolby Vision on a weak box). */
    DEVICE_CANNOT_DECODE,
}

/** What kind of failure the player reported. */
enum class FailureKind {
    /** HTTP 404 / 410. */
    GONE,

    /** Container or manifest the extractor could not read. */
    FORMAT,

    /** The live position fell out of the HLS window. */
    BEHIND_LIVE_WINDOW,

    /** No decoder on this device can play the video: retrying will not help. */
    DECODER,

    /** Network error, server error, decoder hiccup... */
    OTHER,
}

sealed interface RecoveryAction {
    /** A `.ts` link that redirects to an HLS playlist: open it again as HLS. */
    data object RetryAsHls : RecoveryAction

    /** Jump back to the live edge without a new connection. */
    data object RejoinLive : RecoveryAction

    data class Restart(
        val delayMs: Long,
    ) : RecoveryAction

    data class GiveUp(
        val error: PlaybackError,
    ) : RecoveryAction
}

/**
 * Decides how a live channel recovers from failures. Lessons from VesperTV:
 * - a live stream that ENDS is reconnected, not stopped;
 * - HTTP 404 / 410 gives up at once (endless spinner on a dead link otherwise);
 * - 3 failures in a row without ever playing give up;
 * - more than [maxRestarts] restarts within [windowMs] give up, even with playing in between.
 *
 * Pure logic: the clock is passed in, so it is unit-tested on the JVM.
 */
class RecoveryPolicy(
    private val maxRestarts: Int = 6,
    private val windowMs: Long = 60_000,
    private val maxFailuresBeforeReady: Int = 3,
) {
    private val restarts = ArrayDeque<Long>()
    private var failuresSinceReady = 0
    private var triedHls = false

    /** New channel: forget everything. */
    fun reset() {
        restarts.clear()
        failuresSinceReady = 0
        triedHls = false
    }

    /** The stream played: failures before this point no longer count as "never started". */
    fun onReady() {
        failuresSinceReady = 0
    }

    fun onFailure(
        kind: FailureKind,
        alreadyHls: Boolean,
        nowMs: Long,
    ): RecoveryAction {
        if (kind == FailureKind.GONE) return RecoveryAction.GiveUp(PlaybackError.NOT_FOUND)
        if (kind == FailureKind.DECODER) return RecoveryAction.GiveUp(PlaybackError.DEVICE_CANNOT_DECODE)
        if (kind == FailureKind.FORMAT) {
            if (alreadyHls || triedHls) return RecoveryAction.GiveUp(PlaybackError.UNSUPPORTED)
            triedHls = true
            return RecoveryAction.RetryAsHls
        }
        failuresSinceReady++
        if (failuresSinceReady >= maxFailuresBeforeReady) return RecoveryAction.GiveUp(PlaybackError.UNREACHABLE)
        return countRestart(nowMs) ?: when (kind) {
            FailureKind.BEHIND_LIVE_WINDOW -> RecoveryAction.RejoinLive
            else -> RecoveryAction.Restart(backoffMs(failuresSinceReady))
        }
    }

    /** A live stream reached its end: the server closed it. Reconnect at once. */
    fun onLiveEnded(nowMs: Long): RecoveryAction = countRestart(nowMs) ?: RecoveryAction.Restart(0)

    private fun countRestart(nowMs: Long): RecoveryAction? {
        restarts.addLast(nowMs)
        while (restarts.isNotEmpty() && nowMs - restarts.first() > windowMs) restarts.removeFirst()
        return if (restarts.size > maxRestarts) RecoveryAction.GiveUp(PlaybackError.RESTART_LOOP) else null
    }

    private fun backoffMs(attempt: Int): Long = (1_000L shl (attempt - 1).coerceIn(0, 3)).coerceAtMost(8_000)
}
