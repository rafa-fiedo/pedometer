package app.pedometer.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StepChartTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    private val today = d("2026-11-10") // a Tuesday
    private val november = listOf( // same data as StepStatsTest
        DayCount(d("2026-11-04"), 7_395), DayCount(d("2026-11-05"), 9_640),
        DayCount(d("2026-11-06"), 8_115), DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-08"), 4_310), DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    private fun week(date: String, first: DayOfWeek = DayOfWeek.MONDAY) =
        StepChart.periodContaining(ChartRange.WEEK, d(date), first)

    @Test
    fun weekStartsOnThePhonesFirstDay() {
        assertEquals(ChartPeriod(ChartRange.WEEK, d("2026-11-09"), d("2026-11-15")), week("2026-11-10"))
        assertEquals(
            ChartPeriod(ChartRange.WEEK, d("2026-11-08"), d("2026-11-14")),
            week("2026-11-10", DayOfWeek.SUNDAY),
        )
    }

    @Test
    fun weekAcrossTheYearEnd() =
        assertEquals(ChartPeriod(ChartRange.WEEK, d("2026-12-28"), d("2027-01-03")), week("2027-01-01"))

    @Test
    fun monthRunsFromTheFirstToTheLastDay() {
        assertEquals(
            d("2026-02-28"),
            StepChart.periodContaining(ChartRange.MONTH, d("2026-02-14"), DayOfWeek.MONDAY).endInclusive,
        )
        assertEquals(
            ChartPeriod(ChartRange.MONTH, d("2028-02-01"), d("2028-02-29")),
            StepChart.periodContaining(ChartRange.MONTH, d("2028-02-10"), DayOfWeek.MONDAY),
        )
    }

    @Test
    fun leapYearHas366Days() {
        val year = StepChart.periodContaining(ChartRange.YEAR, d("2028-06-01"), DayOfWeek.MONDAY)
        assertEquals(ChartPeriod(ChartRange.YEAR, d("2028-01-01"), d("2028-12-31")), year)
        assertEquals(366, StepChart.chart(emptyList(), year, d("2028-06-01"), 10_000).days.size)
    }

    @Test
    fun shiftMovesWholePeriods() {
        val m = DayOfWeek.MONDAY
        assertEquals(week("2026-11-02"), StepChart.shift(week("2026-11-10"), -1, m))
        assertEquals(
            ChartPeriod(ChartRange.MONTH, d("2026-10-01"), d("2026-10-31")),
            StepChart.shift(StepChart.periodContaining(ChartRange.MONTH, today, m), -1, m),
        )
        assertEquals(
            ChartPeriod(ChartRange.YEAR, d("2027-01-01"), d("2027-12-31")),
            StepChart.shift(StepChart.periodContaining(ChartRange.YEAR, today, m), 1, m),
        )
    }

    @Test
    fun currentWeek() {
        val c = StepChart.chart(november, week("2026-11-10"), today, 10_000)
        assertEquals(listOf(11_204, 6_482, null, null, null, null, null), c.days.map { it.steps })
        assertEquals(17_686, c.total)
        assertEquals(8_843, c.averagePerDay) // two days so far
        assertEquals(12_325, c.scaleMax) // 11,204 + 10%, rounded up
        assertTrue(c.canGoBack)
        assertFalse(c.canGoForward)
    }

    @Test
    fun pastWeekAveragesOnlyFromTheFirstCountedDay() {
        val c = StepChart.chart(november, week("2026-11-02"), today, 10_000)
        assertEquals(listOf(0, 0, 7_395, 9_640, 8_115, 12_870, 4_310), c.days.map { it.steps })
        assertEquals(8_466, c.averagePerDay) // 42,330 over 5 days
        assertFalse(c.canGoBack)
        assertTrue(c.canGoForward)
    }

    @Test
    fun monthAverageRoundsHalfUp() {
        val month = StepChart.periodContaining(ChartRange.MONTH, today, DayOfWeek.MONDAY)
        val c = StepChart.chart(november, month, today, 10_000)
        assertEquals(30, c.days.size)
        assertEquals(60_016, c.total)
        assertEquals(8_574, c.averagePerDay) // 8,573.7
        assertFalse(c.canGoBack)
    }

    @Test
    fun nothingRecorded() {
        val c = StepChart.chart(emptyList(), week("2026-11-10"), today, 10_000)
        assertEquals(listOf(0, 0, null, null, null, null, null), c.days.map { it.steps })
        assertEquals(0, c.total)
        assertEquals(0, c.averagePerDay)
        assertEquals(11_000, c.scaleMax)
        assertFalse(c.canGoBack)
        assertFalse(c.canGoForward)
    }

    @Test
    fun rowsAfterTodayCountAsFuture() {
        val c = StepChart.chart(november + DayCount(d("2026-11-12"), 5_000), week("2026-11-10"), today, 10_000)
        assertNull(c.days[3].steps)
        assertEquals(17_686, c.total)
    }

    @Test
    fun rowsForTheSameDayAreAdded() {
        val c = StepChart.chart(listOf(DayCount(today, 100), DayCount(today, 50)), week("2026-11-10"), today, 10_000)
        assertEquals(150, c.days[1].steps)
    }
}
