package app.pedometer.ui.scores

import app.pedometer.core.ChartRange
import kotlinx.datetime.LocalDate
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ChartControlsTest {
    private val day = LocalDate.parse("2026-11-10")

    @Test
    fun eachViewKeepsItsOwnPosition() {
        val c = ChartControls()
            .back(canGoBack = true)
            .back(canGoBack = true)
            .withRange(ChartRange.MONTH)
            .withRange(ChartRange.WEEK)
        assertEquals(2, c.currentPeriodsBack)
        assertEquals(0, c.withRange(ChartRange.MONTH).currentPeriodsBack)
    }

    @Test
    fun changingViewOrPeriodClearsTheSelection() {
        val selected = ChartControls().back(canGoBack = true).withSelected(day)
        assertEquals(day, selected.selected)
        assertNull(selected.withRange(ChartRange.YEAR).selected)
        assertNull(selected.back(canGoBack = true).selected)
        assertNull(selected.forward(canGoForward = true).selected)
    }

    @Test
    fun arrowsDoNothingAtTheLimits() {
        val start = ChartControls().withSelected(day)
        assertEquals(start, start.back(canGoBack = false))
        assertEquals(start, start.forward(canGoForward = false))
    }

    @Test
    fun neverGoesPastTheCurrentPeriod() =
        assertEquals(0, ChartControls().forward(canGoForward = true).currentPeriodsBack)
}
