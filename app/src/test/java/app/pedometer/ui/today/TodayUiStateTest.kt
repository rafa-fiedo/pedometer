package app.pedometer.ui.today

import app.pedometer.core.DayCount
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class TodayUiStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    private val days = listOf(
        DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    @Test
    fun mapsTotalsAndLabels() {
        val s = todayUiState(days, 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US)
        assertEquals("6,482", s.stepsText)
        assertEquals("of 10,000 steps", s.goalText)
        assertEquals(0.6482f, s.progress, 0.0001f)
        assertEquals("Tuesday, 10 November", s.dateLabel)
        assertEquals("17,686", s.weekText)
        assertEquals("30,556", s.monthText)
        assertEquals(10_000, s.goal)
    }

    @Test
    fun progressStopsAtFull() =
        assertEquals(1f, todayUiState(days, 5_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US).progress)

    @Test
    fun emptyDataShowsZero() {
        val s = todayUiState(emptyList(), 10_000, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US)
        assertEquals("0", s.stepsText)
        assertEquals(0f, s.progress)
    }
}
