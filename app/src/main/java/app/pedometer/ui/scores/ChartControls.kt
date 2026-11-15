package app.pedometer.ui.scores

import app.pedometer.core.ChartRange
import kotlinx.datetime.LocalDate

/**
 * Which chart is shown, how many periods back each view is, and the selected day. Each view
 * keeps its own position; changing view or period clears the selection.
 */
data class ChartControls(
    val range: ChartRange = ChartRange.WEEK,
    val periodsBack: Map<ChartRange, Int> = emptyMap(),
    val selected: LocalDate? = null,
) {
    val currentPeriodsBack: Int get() = periodsBack[range] ?: 0

    fun withRange(range: ChartRange): ChartControls = copy(range = range, selected = null)

    fun withSelected(date: LocalDate?): ChartControls = copy(selected = date)

    /** One period earlier, unless the chart is already at the first counted day. */
    fun back(canGoBack: Boolean): ChartControls = if (canGoBack) moved(1) else this

    /** One period later, unless the chart is already at the current period. */
    fun forward(canGoForward: Boolean): ChartControls = if (canGoForward) moved(-1) else this

    private fun moved(by: Int): ChartControls {
        val back = (currentPeriodsBack + by).coerceAtLeast(0)
        return copy(periodsBack = periodsBack + (range to back), selected = null)
    }
}
