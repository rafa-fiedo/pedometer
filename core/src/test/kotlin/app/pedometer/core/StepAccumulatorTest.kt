package app.pedometer.core

import kotlin.test.Test
import kotlin.test.assertEquals

class StepAccumulatorTest {
    private val prev = AccumulatorState(lastSensorValue = 500, lastBootCount = 3)

    private fun check(previous: AccumulatorState?, value: Long, boot: Int, add: Int, last: Long) {
        val r = StepAccumulator.apply(previous, value, boot)
        assertEquals(add, r.stepsToAdd)
        assertEquals(AccumulatorState(last, boot), r.state)
    }

    @Test
    fun firstReadingSetsBaseline() = check(null, 500, 3, add = 0, last = 500)

    @Test
    fun sameBootAddsDifference() = check(prev, 525, 3, add = 25, last = 525)

    @Test
    fun repeatedValueAddsNothing() = check(prev, 500, 3, add = 0, last = 500)

    @Test
    fun rebootAddsFullValue() = check(prev, 40, 4, add = 40, last = 40)

    @Test
    fun rebootWithHigherValueStillAddsFullValue() = check(prev, 900, 4, add = 900, last = 900)

    @Test
    fun lowerValueOnSameBootRebaselines() = check(prev, 480, 3, add = 0, last = 480)

    @Test
    fun negativeValueIsTreatedAsZero() = check(prev, -5, 4, add = 0, last = 0)

    @Test
    fun hugeJumpIsClamped() =
        check(AccumulatorState(0, 3), 5_000_000_000, 3, add = Int.MAX_VALUE, last = 5_000_000_000)
}
