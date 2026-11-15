package app.pedometer.ui.scores

import app.pedometer.core.BodySize
import app.pedometer.core.ChartPeriod
import app.pedometer.core.ChartRange
import app.pedometer.core.DayCount
import app.pedometer.core.StepChart
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ChartUiTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    private val today = d("2026-11-10") // a Tuesday
    private val november = listOf(
        DayCount(d("2026-11-04"), 7_395), DayCount(d("2026-11-05"), 9_640),
        DayCount(d("2026-11-06"), 8_115), DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-08"), 4_310), DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    private fun ui(
        range: ChartRange,
        date: LocalDate = today,
        first: DayOfWeek = DayOfWeek.MONDAY,
        selected: LocalDate? = null,
        body: BodySize? = null,
    ): ChartUiState {
        val period = StepChart.periodContaining(range, date, first)
        return chartUiState(StepChart.chart(november, period, today, 10_000), selected, Locale.US, body)
    }

    @Test
    fun weekLabelAndAxis() {
        val c = ui(ChartRange.WEEK)
        assertEquals("9–15 Nov", c.periodLabel)
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), c.axisLabels.map { it.text })
        assertEquals((0..6).toList(), c.axisLabels.map { it.index })
        assertEquals("17,686", c.totalText)
        assertEquals("8,843", c.averageText)
        assertEquals("10,000", c.goalText)
        assertTrue(c.bars[0].reachedGoal)
        assertFalse(c.bars[1].reachedGoal)
    }

    @Test
    fun sundayWeekAxisStartsWithS() {
        val c = ui(ChartRange.WEEK, first = DayOfWeek.SUNDAY)
        assertEquals("S", c.axisLabels.first().text)
        assertEquals("8–14 Nov", c.periodLabel)
    }

    @Test
    fun labelsAcrossMonthsAndYears() {
        assertEquals(
            "30 Nov – 6 Dec",
            formatPeriodLabel(ChartPeriod(ChartRange.WEEK, d("2026-11-30"), d("2026-12-06")), Locale.US),
        )
        assertEquals(
            "28 Dec 2026 – 3 Jan 2027",
            formatPeriodLabel(ChartPeriod(ChartRange.WEEK, d("2026-12-28"), d("2027-01-03")), Locale.US),
        )
        assertEquals(
            "November 2026",
            formatPeriodLabel(ChartPeriod(ChartRange.MONTH, d("2026-11-01"), d("2026-11-30")), Locale.US),
        )
        assertEquals(
            "2026",
            formatPeriodLabel(ChartPeriod(ChartRange.YEAR, d("2026-01-01"), d("2026-12-31")), Locale.US),
        )
    }

    @Test
    fun monthAxisMarksTheLastDay() {
        assertEquals(
            listOf(AxisLabel(0, "1"), AxisLabel(9, "10"), AxisLabel(19, "20"), AxisLabel(29, "30")),
            ui(ChartRange.MONTH).axisLabels,
        )
        assertEquals(AxisLabel(27, "28"), ui(ChartRange.MONTH, date = d("2026-02-10")).axisLabels.last())
    }

    @Test
    fun yearAxisHasMonthInitials() {
        val labels = ui(ChartRange.YEAR).axisLabels
        assertEquals(12, labels.size)
        assertEquals("JFMAMJJASOND", labels.joinToString("") { it.text })
        assertEquals(31, labels[1].index)
        assertEquals(59, labels[2].index)
    }

    @Test
    fun selectedDayText() {
        assertEquals(
            SelectedDay(d("2026-11-10"), "Tue 10 Nov", "6,482", kcalText = null),
            ui(ChartRange.WEEK, selected = d("2026-11-10")).selected,
        )
        assertNull(ui(ChartRange.WEEK, selected = d("2026-11-12")).selected) // future
        assertNull(ui(ChartRange.WEEK, selected = d("2026-10-01")).selected) // outside the week
    }

    @Test
    fun kcalForThePeriodAndTheSelectedDay() {
        val withBody = ui(ChartRange.WEEK, selected = d("2026-11-10"), body = BodySize(178, 72))
        assertEquals("470", withBody.kcalText)
        assertEquals("172", withBody.selected?.kcalText)
        val withoutBody = ui(ChartRange.WEEK, selected = d("2026-11-10"))
        assertNull(withoutBody.kcalText)
        assertNull(withoutBody.selected?.kcalText)
    }

    @Test
    fun dayIndexAtClamps() {
        assertEquals(0, dayIndexAt(0f, 700f, 7))
        assertEquals(3, dayIndexAt(350f, 700f, 7))
        assertEquals(6, dayIndexAt(699.9f, 700f, 7))
        assertEquals(0, dayIndexAt(-5f, 700f, 7))
        assertEquals(6, dayIndexAt(800f, 700f, 7))
    }
}
