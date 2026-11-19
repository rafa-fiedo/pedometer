package app.pedometer.counting

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Instant

/** Timestamps further back than this are not trusted, and the reading counts as taken now. */
private const val MAX_STEP_AGE_NANOS = 48L * 3_600_000_000_000L

/**
 * When the last step of a step sensor reading was taken, on the wall clock.
 *
 * Android stamps each step counter event with the time of its last step, on the
 * [android.os.SystemClock.elapsedRealtimeNanos] clock. That time can be hours before the
 * event reaches the app: some phones (Samsung among them) hold readings back while the
 * screen is off and hand them over in the morning. Missing, future or implausibly old
 * timestamps fall back to [nowMillis].
 */
fun lastStepTimeMillis(eventNanos: Long, nowElapsedNanos: Long, nowMillis: Long): Long {
    val ageNanos = nowElapsedNanos - eventNanos
    if (eventNanos <= 0 || ageNanos < 0 || ageNanos > MAX_STEP_AGE_NANOS) return nowMillis
    return nowMillis - ageNanos / 1_000_000
}

/** The phone's local date at [millis] on the wall clock. */
fun localDateAt(millis: Long): LocalDate =
    Instant.fromEpochMilliseconds(millis).toLocalDateTime(TimeZone.currentSystemDefault()).date
