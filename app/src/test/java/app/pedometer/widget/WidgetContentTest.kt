package app.pedometer.widget

import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetContentTest {
    private val state = WidgetState("6,482", "of 10,000", 0.65f, "17,686")

    private fun widget(width: WidgetWidth, assertions: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            provideComposable { WidgetContent(state, width) }
            assertions()
        }

    @Test
    fun oneCellShowsOnlyTheCount() = widget(WidgetWidth.ONE) {
        onNode(hasText("6,482")).assertExists()
        onNode(hasText("of 10,000")).assertDoesNotExist()
    }

    @Test
    fun twoCellsAddTheGoal() = widget(WidgetWidth.TWO) {
        onNode(hasText("6,482")).assertExists()
        onNode(hasText("of 10,000")).assertExists()
        onNode(hasText("this week")).assertDoesNotExist()
    }

    @Test
    fun fourCellsAddTheWeekTotal() = widget(WidgetWidth.FOUR) {
        onNode(hasText("6,482")).assertExists()
        onNode(hasText("of 10,000")).assertExists()
        onNode(hasText("17,686")).assertExists()
        onNode(hasText("this week")).assertExists()
    }
}
