package app.pedometer.core

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class StepStatsTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    private val today = d("2026-11-10") // a Tuesday
    private val november = listOf(
        DayCount(d("2026-11-04"), 7_395), DayCount(d("2026-11-05"), 9_640),
        DayCount(d("2026-11-06"), 8_115), DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-08"), 4_310), DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    @Test
    fun totalsWithMondayWeek() {
        val s = StepStats.summarize(november, today, DayOfWeek.MONDAY)
        assertEquals(6_482, s.today)
        assertEquals(17_686, s.thisWeek)
        assertEquals(60_016, s.thisMonth)
    }

    @Test
    fun weekStartingSunday() =
        assertEquals(21_996, StepStats.summarize(november, today, DayOfWeek.SUNDAY).thisWeek)

    @Test
    fun records() {
        val days = november + DayCount(d("2026-06-14"), 18_342) + DayCount(d("2025-12-30"), 20_000)
        val s = StepStats.summarize(days, today, DayOfWeek.MONDAY)
        assertEquals(DayCount(d("2026-11-07"), 12_870), s.bestDayThisMonth)
        assertEquals(DayCount(d("2026-06-14"), 18_342), s.bestDayThisYear)
    }

    @Test
    fun tiesGoToEarliestDay() {
        val days = november + DayCount(d("2026-11-03"), 12_870)
        assertEquals(d("2026-11-03"), StepStats.summarize(days, today, DayOfWeek.MONDAY).bestDayThisMonth?.date)
    }

    @Test
    fun noDataOrOnlyZeros() {
        for (days in listOf(emptyList(), listOf(DayCount(today, 0)))) {
            val s = StepStats.summarize(days, today, DayOfWeek.MONDAY)
            assertEquals(StepSummary(0, 0, 0, null, null), s)
        }
    }

    @Test
    fun futureRowsAreIgnored() {
        val days = november + DayCount(d("2026-11-11"), 99_999)
        val s = StepStats.summarize(days, today, DayOfWeek.MONDAY)
        assertEquals(60_016, s.thisMonth)
        assertEquals(12_870, s.bestDayThisMonth?.steps)
        assertEquals(d("2026-11-10"), StepStats.history(days, today, 10_000).first().date)
    }

    @Test
    fun firstOfJanuary() {
        val days = listOf(
            DayCount(d("2025-12-29"), 5_000),
            DayCount(d("2025-12-31"), 9_000),
            DayCount(d("2026-01-01"), 3_000),
        )
        val s = StepStats.summarize(days, d("2026-01-01"), DayOfWeek.MONDAY)
        assertEquals(17_000, s.thisWeek)
        assertEquals(3_000, s.thisMonth)
        assertEquals(DayCount(d("2026-01-01"), 3_000), s.bestDayThisYear)
    }

    @Test
    fun historyIsNewestFirstWithGapsAndGoalMarks() {
        val days = listOf(
            DayCount(d("2026-11-07"), 12_870),
            DayCount(d("2026-11-09"), 10_000),
            DayCount(d("2026-11-10"), 6_482),
        )
        assertEquals(
            listOf(
                HistoryDay(d("2026-11-10"), 6_482, false),
                HistoryDay(d("2026-11-09"), 10_000, true),
                HistoryDay(d("2026-11-08"), 0, false),
                HistoryDay(d("2026-11-07"), 12_870, true),
            ),
            StepStats.history(days, today, 10_000),
        )
    }

    @Test
    fun historyRunsThroughTodayAndIsEmptyWithoutRecords() {
        assertEquals(4, StepStats.history(listOf(DayCount(d("2026-11-07"), 100)), today, 10_000).size)
        assertEquals(emptyList(), StepStats.history(emptyList(), today, 10_000))
    }
}
