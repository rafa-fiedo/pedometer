package app.pedometer.counting

import org.junit.Test
import kotlin.test.assertEquals

class StepTimeTest {
    private val hour = 3_600_000_000_000L // in nanoseconds
    private val nowElapsed = 50 * hour
    private val nowMillis = 1_800_000_000_000L

    private fun at(eventNanos: Long) = lastStepTimeMillis(eventNanos, nowElapsed, nowMillis)

    @Test
    fun aHeldBackReadingKeepsTheTimeOfItsLastStep() {
        assertEquals(nowMillis - 3 * 3_600_000L, at(nowElapsed - 3 * hour))
    }

    @Test
    fun aFreshReadingIsNow() {
        assertEquals(nowMillis, at(nowElapsed))
    }

    @Test
    fun aTimestampInTheFutureIsTreatedAsNow() {
        assertEquals(nowMillis, at(nowElapsed + hour))
    }

    @Test
    fun aTimestampOlderThanTwoDaysIsTreatedAsNow() {
        assertEquals(nowMillis, at(nowElapsed - 49 * hour))
    }

    @Test
    fun aMissingTimestampIsTreatedAsNow() {
        assertEquals(nowMillis, at(0))
    }
}
