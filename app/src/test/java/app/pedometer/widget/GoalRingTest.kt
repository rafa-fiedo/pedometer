package app.pedometer.widget

import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * The progress arc is a white mask the widget tints with the theme colour, so the launcher can
 * recolour it after a light/dark switch. Right middle is reached first going clockwise from
 * the top; just left of the top is reached last.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalRingTest {
    private fun arc(progress: Float) = drawGoalArc(100, progress)
    private fun drawn(progress: Float, x: Int, y: Int) = Color.alpha(arc(progress).getPixel(x, y)) == 255
    private fun empty(progress: Float, x: Int, y: Int) = Color.alpha(arc(progress).getPixel(x, y)) == 0

    @Test
    fun theArcIsAWhiteMask() = assertEquals(Color.WHITE, arc(0.5f).getPixel(96, 50))

    @Test
    fun halfProgressCoversTheRightHalfOnly() {
        assertEquals(true, drawn(0.5f, 96, 50))
        assertEquals(true, empty(0.5f, 4, 50))
    }

    @Test
    fun nothingOutsideTheRingOrInTheMiddle() {
        assertEquals(true, empty(1f, 50, 50))
        assertEquals(true, empty(1f, 1, 1))
    }

    @Test
    fun noProgressDrawsNothing() {
        assertEquals(true, empty(0f, 50, 3))
        assertEquals(true, empty(-0.2f, 96, 50))
    }

    @Test
    fun notFullBeforeTheGoal() = assertEquals(true, empty(0.97f, 43, 4))

    @Test
    fun fullAtAndPastTheGoal() {
        for (progress in listOf(1f, 1.7f)) {
            assertEquals(true, drawn(progress, 4, 50), "left middle at $progress")
            assertEquals(true, drawn(progress, 43, 4), "just left of the top at $progress")
        }
    }
}
