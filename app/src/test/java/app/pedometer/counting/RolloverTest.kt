package app.pedometer.counting

import org.junit.Test
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class RolloverTest {
    private fun at(time: String) = ZonedDateTime.parse("$time+01:00[Europe/Paris]")

    @Test
    fun beforeTriggerUsesSameDay() =
        assertEquals(at("2026-11-10T23:48:00"), nextRolloverTrigger(at("2026-11-10T10:00:00")))

    @Test
    fun atOrAfterTriggerUsesNextDay() {
        val next = at("2026-11-11T23:48:00")
        assertEquals(next, nextRolloverTrigger(at("2026-11-10T23:48:00")))
        assertEquals(next, nextRolloverTrigger(at("2026-11-10T23:59:30")))
    }

    @Test
    fun theDeliveryWindowEndsBeforeMidnight() {
        val trigger = nextRolloverTrigger(at("2026-11-10T10:00:00"))
        val latestDelivery = trigger.plusNanos(ROLLOVER_WINDOW_MS * 1_000_000)
        assertEquals(at("2026-11-10T23:58:00"), latestDelivery)
    }
}
