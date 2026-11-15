package app.pedometer.core

import kotlinx.datetime.LocalDate

/** The rule for the goal-reached notification: at most once per calendar day. */
object GoalAlert {
    /**
     * True when the alert is switched on, today's [steps] have reached the [goal], and no
     * alert has been sent [today] yet ([lastAlertDate] is the day the last one was sent).
     */
    fun shouldNotify(
        steps: Int,
        goal: Int,
        today: LocalDate,
        lastAlertDate: LocalDate?,
        enabled: Boolean,
    ): Boolean = enabled && steps >= goal && lastAlertDate != today
}
