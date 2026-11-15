package app.pedometer.ui.scores

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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

/** Checks what the chart actually draws, by reading pixels from the rendered window. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StepChartPixelTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val colors = lightColorScheme()
    private fun d(iso: String) = LocalDate.parse(iso)

    private val november = listOf(
        DayCount(d("2026-11-09"), 11_204),
        DayCount(d("2026-11-10"), 6_482),
    )

    // The selected day can change after the content is set, as it does on screen.
    private val selected = mutableStateOf<LocalDate?>(null)

    private fun show(days: List<DayCount>, range: ChartRange, today: LocalDate, selected: LocalDate? = null) {
        this.selected.value = selected
        val period = StepChart.periodContaining(range, today, DayOfWeek.MONDAY)
        val data = StepChart.chart(days, period, today, 10_000)
        compose.setContent {
            val chart = chartUiState(data, this.selected.value, Locale.US, body = null)
            MaterialTheme(colorScheme = colors) { StepChartView(chart, "chart", onSelect = {}) }
        }
        compose.waitForIdle()
    }

    /** The colour at a point given as fractions of the chart's width and height (0,0 = top left). */
    private fun pixel(xFraction: Float, yFraction: Float): Int {
        val bounds = compose.onNodeWithTag(STEP_CHART_TAG).fetchSemanticsNode().boundsInWindow
        val view = compose.activity.window.decorView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        val x = bounds.left + bounds.width * xFraction
        val y = bounds.top + bounds.height * yFraction
        return bitmap.getPixel(x.toInt(), y.toInt())
    }

    @Test
    fun aSelectedBarStandsOut() {
        val today = d("2026-11-10")
        show(november, ChartRange.WEEK, today)
        assertEquals(colors.surfaceVariant.toArgb(), pixel(1.5f / 7, 0.95f))

        selected.value = today
        compose.waitForIdle()
        assertEquals(colors.tertiary.toArgb(), pixel(1.5f / 7, 0.95f))
    }

    @Test
    fun theSelectedDayIsMarkedOnTheYearLine() {
        val today = d("2026-11-10")
        show(november, ChartRange.YEAR, today, selected = d("2026-01-10"))
        // 10 January has no steps, so its point sits on the bottom edge.
        assertEquals(colors.tertiary.toArgb(), pixel(9.5f / 365, 0.995f))
    }

    @Test
    fun aSingleDayOnTheYearLineIsVisible() {
        val newYear = d("2027-01-01")
        show(listOf(DayCount(newYear, 5_500)), ChartRange.YEAR, newYear)
        // 5,500 of a scale topping out at 11,000: halfway up.
        assertEquals(colors.primary.toArgb(), pixel(0.5f / 365, 0.5f))
    }

    @Test
    fun axisLabelsAreNotReadSeparately() {
        show(november, ChartRange.YEAR, d("2026-11-10"))
        compose.onNodeWithText("J").assertDoesNotExist()
    }
}
