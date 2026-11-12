package app.pedometer.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GoalTest {
    @Test
    fun parsesValidGoals() {
        assertEquals(10_000, Goal.parse("10000"))
        assertEquals(8_000, Goal.parse(" 8000 "))
        assertEquals(500, Goal.parse("500"))
        assertEquals(100_000, Goal.parse("100000"))
    }

    @Test
    fun rejectsInvalidGoals() {
        for (text in listOf("499", "100001", "", "abc", "12.5", "-600", "10,000", "99999999999999999999")) {
            assertNull(Goal.parse(text), text)
        }
    }

    @Test
    fun validatesRange() {
        assertTrue(Goal.isValid(500))
        assertTrue(Goal.isValid(100_000))
        assertFalse(Goal.isValid(499))
        assertFalse(Goal.isValid(100_001))
    }
}
