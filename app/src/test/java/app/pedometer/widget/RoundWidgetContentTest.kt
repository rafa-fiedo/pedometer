package app.pedometer.widget

import android.graphics.Bitmap
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.GlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasRunCallbackClickAction
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoundWidgetContentTest {
    private val state = WidgetState("6,482", "of 10,000", 0.65f, "17,686")
    private val ring = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)

    private fun widget(assertions: GlanceAppWidgetUnitTest.() -> Unit) =
        runGlanceAppWidgetUnitTest {
            setContext(ApplicationProvider.getApplicationContext())
            setAppWidgetSize(DpSize(70.dp, 70.dp))
            provideComposable { RoundWidgetContent(state, ring) }
            assertions()
        }

    @Test
    fun showsTodaysSteps() = widget {
        onNode(hasText("6,482")).assertExists()
        onNode(hasText("of 10,000")).assertDoesNotExist()
    }

    @Test
    fun tappingRefreshes() = widget {
        onNode(hasRunCallbackClickAction<RefreshWidgetAction>()).assertExists()
    }

    @Test
    fun sixDigitCountsUseTheSmallerFont() {
        assertEquals(10, roundStepsFontSp("12,870"))
        assertEquals(12, roundStepsFontSp("6,482"))
    }
}
