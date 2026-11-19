package app.pedometer.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus

/** One day's step total. */
data class DayCount(val date: LocalDate, val steps: Int)

data class StepSummary(
    val today: Int,
    val thisWeek: Int,
    val thisMonth: Int,
    /** Null when no day in the current month has any steps. */
    val bestDayThisMonth: DayCount?,
    /** Null when no day in the current year has any steps. */
    val bestDayThisYear: DayCount?,
)

data class HistoryDay(val date: LocalDate, val steps: Int, val goalReached: Boolean)

/**
 * Totals, records and history derived from the per-day counts.
 *
 * Rows dated after [today][summarize] (possible when the phone's clock was set back) are
 * ignored, and the input does not need to be sorted.
 */
object StepStats {
    fun summarize(days: List<DayCount>, today: LocalDate, firstDayOfWeek: DayOfWeek): StepSummary {
        val past = days.filter { it.date <= today }
        val daysIntoWeek = (today.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
        val weekStart = today.minus(daysIntoWeek, DateTimeUnit.DAY)
        val thisMonth = past.filter { it.date.year == today.year && it.date.month == today.month }
        val thisYear = past.filter { it.date.year == today.year }
        return StepSummary(
            today = past.filter { it.date == today }.sumOf { it.steps },
            thisWeek = past.filter { it.date >= weekStart }.sumOf { it.steps },
            thisMonth = thisMonth.sumOf { it.steps },
            bestDayThisMonth = bestDay(thisMonth),
            bestDayThisYear = bestDay(thisYear),
        )
    }

    /** Every day from the first recorded day through [today], newest first; gaps count as 0. */
    fun history(days: List<DayCount>, today: LocalDate, goal: Int): List<HistoryDay> {
        val stepsByDate = days.filter { it.date <= today }
            .groupBy({ it.date }, { it.steps })
            .mapValues { (_, steps) -> steps.sum() }
        val first = stepsByDate.keys.minOrNull() ?: return emptyList()
        return generateSequence(today) { it.minus(1, DateTimeUnit.DAY) }
            .takeWhile { it >= first }
            .map { date ->
                val steps = stepsByDate[date] ?: 0
                HistoryDay(date, steps, goalReached = steps >= goal)
            }
            .toList()
    }

    /** The day with the most steps; ties go to the earliest day, and a day needs at least one step. */
    private fun bestDay(days: List<DayCount>): DayCount? =
        days.filter { it.steps > 0 }.sortedBy { it.date }.maxByOrNull { it.steps }
}
