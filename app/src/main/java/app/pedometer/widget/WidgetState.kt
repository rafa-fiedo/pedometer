package app.pedometer.widget

import app.pedometer.core.DayCount
import app.pedometer.core.StepStats
import app.pedometer.ui.formatSteps
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.util.Locale

/** How many home-screen cells wide the widget is; it shows more as it gets wider. */
enum class WidgetWidth { ONE, TWO, THREE, FOUR }

/** Maps the widget's actual width to a layout. Anything wider than four cells uses four. */
fun widgetWidth(widthDp: Float): WidgetWidth = when {
    widthDp < 130f -> WidgetWidth.ONE
    widthDp < 203f -> WidgetWidth.TWO
    widthDp < 276f -> WidgetWidth.THREE
    else -> WidgetWidth.FOUR
}

/** Everything the widget displays, already formatted. */
data class WidgetState(
    val stepsText: String,
    val goalText: String,
    /** Share of the goal reached, from 0 to 1. */
    val progress: Float,
    val weekText: String,
)

fun widgetState(
    days: List<DayCount>,
    goal: Int,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
): WidgetState {
    val summary = StepStats.summarize(days, today, firstDayOfWeek)
    return WidgetState(
        stepsText = formatSteps(summary.today, locale),
        goalText = "of ${formatSteps(goal, locale)}",
        progress = if (goal > 0) (summary.today.toFloat() / goal).coerceIn(0f, 1f) else 0f,
        weekText = formatSteps(summary.thisWeek, locale),
    )
}
