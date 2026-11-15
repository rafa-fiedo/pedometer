package app.pedometer.ui.scores

import app.pedometer.core.ChartRange
import app.pedometer.core.DayCount
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoresUiStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    private val today = d("2026-11-10")
    private val days = listOf(
        DayCount(d("2026-06-14"), 18_342),
        DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    private fun state(days: List<DayCount>, range: ChartRange = ChartRange.WEEK, periodsBack: Int = 0) =
        scoresUiState(days, today, 10_000, DayOfWeek.MONDAY, Locale.US, range, periodsBack, selected = null, body = null)

    @Test
    fun mapsChartAndRecords() {
        val s = state(days)
        assertEquals("17,686", s.chart.totalText)
        assertEquals(RecordUi("12,870", "Sat 7 November"), s.bestThisMonth)
        assertEquals(RecordUi("18,342", "Sun 14 June"), s.bestThisYear)
    }

    @Test
    fun periodsBackMovesTheChart() =
        assertEquals("October 2026", state(days, ChartRange.MONTH, periodsBack = 1).chart.periodLabel)

    @Test
    fun noRecordsYet() {
        val s = state(emptyList())
        assertEquals("0", s.chart.totalText)
        assertNull(s.bestThisMonth)
        assertNull(s.bestThisYear)
    }
}
