package app.pedometer.widget

import app.pedometer.core.DayCount
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class WidgetStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun mapsStepsGoalProgressAndWeek() {
        val days = listOf(DayCount(d("2026-11-09"), 11_204), DayCount(d("2026-11-10"), 6_482))
        assertEquals(
            WidgetState("6,482", "of 10,000", 0.6482f, "17,686"),
            widgetState(days, 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US),
        )
    }

    @Test
    fun progressStopsAtFullAndEmptyDataIsZero() {
        val overGoal = listOf(DayCount(d("2026-11-10"), 9_000))
        assertEquals(1f, widgetState(overGoal, 5_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US).progress)
        assertEquals(
            WidgetState("0", "of 10,000", 0f, "0"),
            widgetState(emptyList(), 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US),
        )
    }

    @Test
    fun widthBreakpoints() {
        assertEquals(WidgetWidth.ONE, widgetWidth(57f))
        assertEquals(WidgetWidth.ONE, widgetWidth(129f))
        assertEquals(WidgetWidth.TWO, widgetWidth(130f))
        assertEquals(WidgetWidth.THREE, widgetWidth(203f))
        assertEquals(WidgetWidth.FOUR, widgetWidth(276f))
        assertEquals(WidgetWidth.FOUR, widgetWidth(400f))
    }
}
