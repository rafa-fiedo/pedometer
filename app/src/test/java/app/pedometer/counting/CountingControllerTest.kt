package app.pedometer.counting

import org.junit.Test
import kotlin.test.assertEquals

class CountingControllerTest {
    @Test
    fun cannotCountMeansNoMode() {
        assertEquals(CountingMode.NONE, countingMode(canCount = false, showStepCount = true))
        assertEquals(CountingMode.NONE, countingMode(canCount = false, showStepCount = false))
    }

    @Test
    fun stepCountOnIsContinuous() =
        assertEquals(CountingMode.CONTINUOUS, countingMode(canCount = true, showStepCount = true))

    @Test
    fun stepCountOffIsPeriodic() =
        assertEquals(CountingMode.PERIODIC, countingMode(canCount = true, showStepCount = false))
}
