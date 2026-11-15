package app.pedometer.ui.scores

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import app.pedometer.core.ChartRange
import app.pedometer.core.DayCount
import app.pedometer.core.StepChart
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.util.Locale
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepChartViewTest {
    @get:Rule
    val compose = createComposeRule()

    private fun d(iso: String) = LocalDate.parse(iso)

    private val today = d("2026-11-10") // a Tuesday
    private val november = listOf(
        DayCount(d("2026-11-04"), 7_395), DayCount(d("2026-11-05"), 9_640),
        DayCount(d("2026-11-06"), 8_115), DayCount(d("2026-11-07"), 12_870),
        DayCount(d("2026-11-08"), 4_310), DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    // Each call to onSelect is recorded, including calls with null.
    private val selections = mutableListOf<LocalDate?>()

    private fun show(range: ChartRange, selected: LocalDate? = null, description: String = "chart") {
        val period = StepChart.periodContaining(range, today, DayOfWeek.MONDAY)
        val chart = chartUiState(StepChart.chart(november, period, today, 10_000), selected, Locale.US)
        compose.setContent { StepChartView(chart, description, onSelect = { selections += it }) }
    }

    private fun tapAt(fraction: Float) {
        compose.onNodeWithTag(STEP_CHART_TAG).performTouchInput { click(Offset(width * fraction, centerY)) }
        compose.waitForIdle()
    }

    @Test
    fun tappingABarSelectsThatDay() {
        show(ChartRange.WEEK)
        tapAt(1.5f / 7)
        assertEquals(listOf<LocalDate?>(d("2026-11-10")), selections)
    }

    @Test
    fun tappingAFutureDayDoesNothing() {
        show(ChartRange.WEEK)
        tapAt(5.5f / 7)
        assertEquals(emptyList(), selections)
    }

    @Test
    fun tappingTheSelectedDayClearsIt() {
        show(ChartRange.WEEK, selected = d("2026-11-10"))
        tapAt(1.5f / 7)
        assertEquals(listOf<LocalDate?>(null), selections)
    }

    @Test
    fun yearTapPicksTheNearestDay() {
        show(ChartRange.YEAR)
        tapAt(9.5f / 365)
        assertEquals(listOf<LocalDate?>(d("2026-01-10")), selections)
    }

    @Test
    fun describedForScreenReaders() {
        show(ChartRange.WEEK, description = "Week: 17,686 total, 8,843 a day")
        compose.onNodeWithContentDescription("Week: 17,686 total, 8,843 a day").assertExists()
    }
}
