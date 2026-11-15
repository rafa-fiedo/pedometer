package app.pedometer.ui.scores

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.pedometer.core.ChartRange
import app.pedometer.core.DayCount
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
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScoresContentTest {
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

    private val ranges = mutableListOf<ChartRange>()
    private var previousClicks = 0

    private fun show(selected: LocalDate? = null) {
        val state = scoresUiState(
            november, today, 10_000, DayOfWeek.MONDAY, Locale.US, ChartRange.WEEK, 0, selected,
        )
        compose.setContent {
            ScoresContent(
                state,
                onRange = { ranges += it },
                onPrevious = { previousClicks++ },
                onNext = {},
                onSelect = {},
            )
        }
    }

    @Test
    fun showsTheChartSummaryAndRecords() {
        show()
        compose.onNodeWithText("9–15 Nov").assertIsDisplayed()
        compose.onNodeWithText("17,686 total · 8,843 a day").assertIsDisplayed()
        compose.onNodeWithText("Best day this month").assertExists()
        compose.onNodeWithText("Totals").assertDoesNotExist()
    }

    @Test
    fun aSelectedDayReplacesTheSummary() {
        show(selected = d("2026-11-10"))
        compose.onNodeWithText("Tue 10 Nov · 6,482 steps").assertIsDisplayed()
        compose.onNodeWithText("17,686 total · 8,843 a day").assertDoesNotExist()
    }

    @Test
    fun controlsCallBack() {
        show()
        compose.onNodeWithText("Month").performClick()
        assertEquals(listOf(ChartRange.MONTH), ranges)
        compose.onNodeWithContentDescription("Previous period").performClick()
        assertEquals(1, previousClicks)
        compose.onNodeWithContentDescription("Next period").assertIsNotEnabled()
    }
}
