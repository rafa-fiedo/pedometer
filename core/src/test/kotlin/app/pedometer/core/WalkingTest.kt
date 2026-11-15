package app.pedometer.core

import kotlinx.datetime.DayOfWeek
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class WalkingTest {
    private val body = BodySize(178, 72)

    @Test
    fun tenThousandStepsAtTheSpecExample() {
        val walk = Walking.estimate(10_000, body)
        assertEquals(7.387, walk.km, 0.001)
        assertEquals(266, walk.kcal)
    }

    @Test
    fun largerTotalsRoundHalfUp() {
        assertEquals(1_420, Walking.estimate(53_400, body).kcal)
        assertEquals(172, Walking.estimate(6_482, body).kcal)
        assertEquals(470, Walking.estimate(17_686, body).kcal)
    }

    @Test
    fun noStepsNoCalories() = assertEquals(WalkEstimate(0.0, 0), Walking.estimate(0, body))

    @Test
    fun bodySizeNeedsBothValuesInRange() {
        assertEquals(BodySize(178, 72), BodySize.of(178, 72))
        assertNull(BodySize.of(null, 72))
        assertNull(BodySize.of(178, null))
        assertNull(BodySize.of(99, 72))
        assertNull(BodySize.of(178, 301))
    }

    @Test
    fun parseMeasureAcceptsOnlyWholeNumbersInRange() {
        assertEquals(178, BodySize.parseMeasure(" 178 ", BodySize.HEIGHT_RANGE))
        assertEquals(250, BodySize.parseMeasure("250", BodySize.HEIGHT_RANGE))
        for (bad in listOf("99", "251", "17.5", "-80", "abc", "")) {
            assertNull(BodySize.parseMeasure(bad, BodySize.HEIGHT_RANGE), "accepted '$bad'")
        }
    }

    @Test
    fun weekStartResolves() {
        assertEquals(DayOfWeek.SUNDAY, WeekStart.PHONE_DEFAULT.resolve(DayOfWeek.SUNDAY))
        assertEquals(DayOfWeek.MONDAY, WeekStart.MONDAY.resolve(DayOfWeek.SUNDAY))
        assertEquals(DayOfWeek.SUNDAY, WeekStart.SUNDAY.resolve(DayOfWeek.MONDAY))
    }
}
