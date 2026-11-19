package app.pedometer.core

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

enum class ChartRange { WEEK, MONTH, YEAR }

/** One week, month or year, from [start] through [endInclusive]. */
data class ChartPeriod(val range: ChartRange, val start: LocalDate, val endInclusive: LocalDate)

/** One day of a chart; [steps] is null for a day that hasn't happened yet. */
data class ChartDay(val date: LocalDate, val steps: Int?)

data class ChartData(
    val period: ChartPeriod,
    /** Every day of the period, in order. */
    val days: List<ChartDay>,
    val goal: Int,
    val total: Int,
    /** Over the days that have started, not counting days before the first counted day. */
    val averagePerDay: Int,
    /** The top of the chart: the goal or the best day, whichever is larger, plus 10%. */
    val scaleMax: Int,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

/** The numbers behind the week, month and year charts. */
object StepChart {
    fun periodContaining(range: ChartRange, date: LocalDate, firstDayOfWeek: DayOfWeek): ChartPeriod =
        when (range) {
            ChartRange.WEEK -> {
                val daysIntoWeek = (date.dayOfWeek.isoDayNumber - firstDayOfWeek.isoDayNumber + 7) % 7
                val start = date.minus(daysIntoWeek, DateTimeUnit.DAY)
                ChartPeriod(range, start, start.plus(6, DateTimeUnit.DAY))
            }
            ChartRange.MONTH -> {
                val start = LocalDate(date.year, date.month, 1)
                ChartPeriod(range, start, start.plus(1, DateTimeUnit.MONTH).minus(1, DateTimeUnit.DAY))
            }
            ChartRange.YEAR -> ChartPeriod(range, LocalDate(date.year, 1, 1), LocalDate(date.year, 12, 31))
        }

    /** The period [by] whole periods later (negative: earlier). */
    fun shift(period: ChartPeriod, by: Int, firstDayOfWeek: DayOfWeek): ChartPeriod {
        val moved = when (period.range) {
            ChartRange.WEEK -> period.start.plus(by * 7, DateTimeUnit.DAY)
            ChartRange.MONTH -> period.start.plus(by, DateTimeUnit.MONTH)
            ChartRange.YEAR -> period.start.plus(by, DateTimeUnit.YEAR)
        }
        return periodContaining(period.range, moved, firstDayOfWeek)
    }

    fun chart(days: List<DayCount>, period: ChartPeriod, today: LocalDate, goal: Int): ChartData {
        val stepsByDate = days.filter { it.date <= today }
            .groupBy({ it.date }, { it.steps })
            .mapValues { (_, steps) -> steps.sum() }
        val firstCounted = stepsByDate.keys.minOrNull()

        val chartDays = generateSequence(period.start) { it.plus(1, DateTimeUnit.DAY) }
            .takeWhile { it <= period.endInclusive }
            .map { date -> ChartDay(date, if (date > today) null else stepsByDate[date] ?: 0) }
            .toList()

        val total = chartDays.sumOf { it.steps ?: 0 }
        val daysStarted = if (firstCounted == null) {
            0
        } else {
            chartDays.count { it.steps != null && it.date >= firstCounted }
        }
        // Rounded half up, in whole numbers to stay exact.
        val average = if (daysStarted == 0) 0 else ((2L * total + daysStarted) / (2L * daysStarted)).toInt()
        val best = chartDays.maxOfOrNull { it.steps ?: 0 } ?: 0

        return ChartData(
            period = period,
            days = chartDays,
            goal = goal,
            total = total,
            averagePerDay = average,
            scaleMax = (maxOf(goal, best) * 11 + 9) / 10,
            canGoBack = firstCounted != null && firstCounted < period.start,
            canGoForward = period.endInclusive < today,
        )
    }
}
