package app.pedometer.ui.history

import app.pedometer.core.DayCount
import app.pedometer.core.StepStats
import app.pedometer.ui.formatDayRow
import app.pedometer.ui.formatMonthTitle
import app.pedometer.ui.formatSteps
import kotlinx.datetime.LocalDate
import java.util.Locale

data class HistoryRow(val label: String, val stepsText: String, val goalReached: Boolean)

data class HistoryMonth(val title: String, val rows: List<HistoryRow>)

/** The history list grouped by month, newest month and newest day first. */
fun historyMonths(
    days: List<DayCount>,
    goal: Int,
    today: LocalDate,
    locale: Locale,
): List<HistoryMonth> =
    StepStats.history(days, today, goal)
        .groupBy { it.date.year to it.date.month }
        .map { (_, monthDays) ->
            HistoryMonth(
                title = formatMonthTitle(monthDays.first().date, locale),
                rows = monthDays.map { day ->
                    HistoryRow(
                        label = formatDayRow(day.date, locale),
                        stepsText = formatSteps(day.steps, locale),
                        goalReached = day.goalReached,
                    )
                },
            )
        }
