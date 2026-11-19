package app.pedometer.ui.scores

import app.pedometer.core.BodySize
import app.pedometer.core.ChartData
import app.pedometer.core.ChartPeriod
import app.pedometer.core.ChartRange
import app.pedometer.core.Walking
import app.pedometer.ui.formatMonthTitle
import app.pedometer.ui.formatSteps
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.floor

data class ChartBar(val date: LocalDate, val steps: Int?, val reachedGoal: Boolean)

/** A label under the chart, centred on the bar at [index]. */
data class AxisLabel(val index: Int, val text: String)

data class SelectedDay(
    val date: LocalDate,
    val dateText: String,
    val stepsText: String,
    /** Null until both height and weight are set. */
    val kcalText: String?,
)

data class ChartUiState(
    val range: ChartRange,
    val periodLabel: String,
    val bars: List<ChartBar>,
    val axisLabels: List<AxisLabel>,
    val goal: Int,
    val goalText: String,
    val scaleMax: Int,
    val totalText: String,
    val averageText: String,
    /** Walking calories for the whole period; null until both height and weight are set. */
    val kcalText: String?,
    /** Null when no day is selected, or the selected day is in the future or another period. */
    val selected: SelectedDay?,
    val canGoBack: Boolean,
    val canGoForward: Boolean,
)

fun chartUiState(data: ChartData, selected: LocalDate?, locale: Locale, body: BodySize?): ChartUiState {
    val bars = data.days.map { day ->
        val steps = day.steps
        ChartBar(day.date, steps, reachedGoal = steps != null && steps >= data.goal)
    }
    val selectedBar = bars.firstOrNull { it.date == selected && it.steps != null }
    return ChartUiState(
        range = data.period.range,
        periodLabel = formatPeriodLabel(data.period, locale),
        bars = bars,
        axisLabels = axisLabels(data.period.range, bars, locale),
        goal = data.goal,
        goalText = formatSteps(data.goal, locale),
        scaleMax = data.scaleMax,
        totalText = formatSteps(data.total, locale),
        averageText = formatSteps(data.averagePerDay, locale),
        kcalText = body?.let { formatSteps(Walking.estimate(data.total, it).kcal, locale) },
        selected = selectedBar?.let {
            val steps = it.steps ?: 0
            SelectedDay(
                it.date,
                format(it.date, "EEE d MMM", locale),
                formatSteps(steps, locale),
                kcalText = body?.let { size -> formatSteps(Walking.estimate(steps, size).kcal, locale) },
            )
        },
        canGoBack = data.canGoBack,
        canGoForward = data.canGoForward,
    )
}

/** "9–15 Nov", "30 Nov – 6 Dec", "28 Dec 2026 – 3 Jan 2027", "November 2026" or "2026". */
fun formatPeriodLabel(period: ChartPeriod, locale: Locale): String {
    val start = period.start
    val end = period.endInclusive
    return when (period.range) {
        ChartRange.WEEK -> when {
            start.year != end.year -> "${format(start, "d MMM yyyy", locale)} – ${format(end, "d MMM yyyy", locale)}"
            start.month != end.month -> "${format(start, "d MMM", locale)} – ${format(end, "d MMM", locale)}"
            else -> "${format(start, "d", locale)}–${format(end, "d MMM", locale)}"
        }
        ChartRange.MONTH -> formatMonthTitle(start, locale)
        ChartRange.YEAR -> format(start, "yyyy", locale)
    }
}

/** The position of the day under [x] on a chart [width] wide with [count] equal slots. */
fun dayIndexAt(x: Float, width: Float, count: Int): Int =
    floor(x / width * count).toInt().coerceIn(0, count - 1)

private fun axisLabels(range: ChartRange, bars: List<ChartBar>, locale: Locale): List<AxisLabel> =
    when (range) {
        ChartRange.WEEK -> bars.mapIndexed { i, bar -> AxisLabel(i, format(bar.date, "EEEEE", locale)) }
        ChartRange.MONTH -> listOf(0, 9, 19, bars.lastIndex).map { i -> AxisLabel(i, bars[i].date.day.toString()) }
        ChartRange.YEAR -> bars.mapIndexedNotNull { i, bar ->
            if (bar.date.day == 1) AxisLabel(i, format(bar.date, "MMMMM", locale)) else null
        }
    }

private fun format(date: LocalDate, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(date.toJavaLocalDate())
