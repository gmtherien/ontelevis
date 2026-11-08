package app.ontelevis.core.model.epg

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Visible time range of the EPG track. Default: 2 h starting one slot before the current slot. */
data class EpgWindow(
    val start: Instant,
    val duration: Duration = Duration.ofHours(2),
) {
    val end: Instant get() = start.plus(duration)

    /** Position of [t] in the window, 0f..1f (clamped). */
    fun fractionOf(t: Instant): Float {
        val total = duration.toMillis().toFloat()
        val offset = Duration.between(start, t).toMillis().toFloat()
        return (offset / total).coerceIn(0f, 1f)
    }

    /** Slot boundaries (every [slot]) inside the window, for the header ticks. */
    fun ticks(slot: Duration = Duration.ofMinutes(30)): List<Instant> =
        generateSequence(start) { it.plus(slot) }.takeWhile { it.isBefore(end) }.toList()

    companion object {
        /** 10:50 -> window starts 10:00 (current 30-min slot floor, minus one slot). */
        fun around(
            now: Instant,
            slot: Duration = Duration.ofMinutes(30),
            duration: Duration = Duration.ofHours(2),
        ): EpgWindow {
            val slotMs = slot.toMillis()
            val floored = Instant.ofEpochMilli(Math.floorDiv(now.toEpochMilli(), slotMs) * slotMs)
            return EpgWindow(floored.minus(slot).truncatedTo(ChronoUnit.MILLIS), duration)
        }
    }
}

enum class ProgramState { PAST, CURRENT, UPCOMING }

fun programState(
    start: Instant,
    end: Instant,
    now: Instant,
): ProgramState =
    when {
        !end.isAfter(now) -> ProgramState.PAST
        start.isAfter(now) -> ProgramState.UPCOMING
        else -> ProgramState.CURRENT
    }

/** Horizontal span of a program in the window as (startFraction, endFraction), or null if not visible. */
fun EpgWindow.spanOf(
    start: Instant,
    end: Instant,
): Pair<Float, Float>? {
    if (!end.isAfter(this.start) || !start.isBefore(this.end)) return null
    return fractionOf(start) to fractionOf(end)
}

/** Progress of a current program, 0f..1f. */
fun progressOf(
    start: Instant,
    end: Instant,
    now: Instant,
): Float {
    val total = Duration.between(start, end).toMillis().toFloat()
    if (total <= 0f) return 0f
    return (Duration.between(start, now).toMillis() / total).coerceIn(0f, 1f)
}

/** Whole minutes left, rounded up ("reste 40 min"). */
fun minutesLeft(
    end: Instant,
    now: Instant,
): Long {
    val ms = Duration.between(now, end).toMillis()
    return if (ms <= 0) 0 else (ms + 59_999) / 60_000
}
