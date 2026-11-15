package app.pedometer.core

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GoalAlertTest {
    private val today = LocalDate.parse("2026-11-12")

    private fun check(steps: Int, last: LocalDate?, enabled: Boolean = true) =
        GoalAlert.shouldNotify(steps, 10_000, today, last, enabled)

    @Test
    fun belowGoalDoesNotNotify() = assertFalse(check(9_999, null))

    @Test
    fun atOrAboveGoalNotifies() {
        assertTrue(check(10_000, null))
        assertTrue(check(12_870, null))
    }

    @Test
    fun alreadySentTodayDoesNotNotifyAgain() = assertFalse(check(12_870, today))

    @Test
    fun sentYesterdayNotifiesAgain() = assertTrue(check(10_000, LocalDate.parse("2026-11-11")))

    @Test
    fun disabledNeverNotifies() = assertFalse(check(12_870, null, enabled = false))
}
