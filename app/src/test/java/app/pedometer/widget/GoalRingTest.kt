package app.pedometer.widget

import android.graphics.Color
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/** Reads pixels from the drawn ring: right middle is reached first going clockwise from the top. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class GoalRingTest {
    private val white = Color.WHITE
    private val gray = Color.GRAY
    private val blue = Color.BLUE

    private fun ring(progress: Float) = drawGoalRing(100, progress, white, gray, blue)

    @Test
    fun halfProgressFillsTheRightHalf() {
        val b = ring(0.5f)
        assertEquals(blue, b.getPixel(96, 50))
        assertEquals(gray, b.getPixel(4, 50))
    }

    @Test
    fun centreIsTheCircleAndCornersAreTransparent() {
        val b = ring(0.5f)
        assertEquals(white, b.getPixel(50, 50))
        assertEquals(0, Color.alpha(b.getPixel(1, 1)))
    }

    @Test
    fun progressIsClamped() {
        assertEquals(blue, ring(1.7f).getPixel(4, 50))
        assertEquals(gray, ring(-0.2f).getPixel(96, 50))
    }
}
