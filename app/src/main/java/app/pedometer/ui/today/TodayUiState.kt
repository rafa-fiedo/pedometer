package app.pedometer.ui.today

import app.pedometer.core.BodySize
import app.pedometer.core.DayCount
import app.pedometer.core.StepStats
import app.pedometer.core.Walking
import app.pedometer.ui.formatKm
import app.pedometer.ui.formatLongDate
import app.pedometer.ui.formatSteps
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.util.Locale

/** Today's walking calories and distance, formatted: "266" and "7.4". */
data class WalkUi(val kcalText: String, val kmText: String)

/** Everything the Today screen displays, already formatted. */
data class TodayUiState(
    val stepsText: String,
    val goalText: String,
    /** Share of the goal reached, from 0 to 1. */
    val progress: Float,
    val dateLabel: String,
    val weekText: String,
    val monthText: String,
    /** The raw goal, used to prefill the goal dialog. */
    val goal: Int,
    /** Null until both height and weight are set. */
    val walk: WalkUi?,
)

fun todayUiState(
    days: List<DayCount>,
    goal: Int,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
    body: BodySize?,
): TodayUiState {
    val summary = StepStats.summarize(days, today, firstDayOfWeek)
    return TodayUiState(
        stepsText = formatSteps(summary.today, locale),
        goalText = "of ${formatSteps(goal, locale)} steps",
        progress = if (goal > 0) (summary.today.toFloat() / goal).coerceIn(0f, 1f) else 0f,
        dateLabel = formatLongDate(today, locale),
        weekText = formatSteps(summary.thisWeek, locale),
        monthText = formatSteps(summary.thisMonth, locale),
        goal = goal,
        walk = body?.let {
            val estimate = Walking.estimate(summary.today, it)
            WalkUi(formatSteps(estimate.kcal, locale), formatKm(estimate.km, locale))
        },
    )
}
