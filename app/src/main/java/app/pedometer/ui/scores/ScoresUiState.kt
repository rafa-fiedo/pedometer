package app.pedometer.ui.scores

import app.pedometer.core.DayCount
import app.pedometer.core.StepStats
import app.pedometer.ui.formatRecordDate
import app.pedometer.ui.formatSteps
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.util.Locale

data class RecordUi(val stepsText: String, val dateText: String)

data class ScoresUiState(
    val todayText: String,
    val weekText: String,
    val monthText: String,
    /** Null when no day this month has any steps. */
    val bestThisMonth: RecordUi?,
    /** Null when no day this year has any steps. */
    val bestThisYear: RecordUi?,
)

fun scoresUiState(
    days: List<DayCount>,
    today: LocalDate,
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
): ScoresUiState {
    val summary = StepStats.summarize(days, today, firstDayOfWeek)
    fun record(day: DayCount?) =
        day?.let { RecordUi(formatSteps(it.steps, locale), formatRecordDate(it.date, locale)) }
    return ScoresUiState(
        todayText = formatSteps(summary.today, locale),
        weekText = formatSteps(summary.thisWeek, locale),
        monthText = formatSteps(summary.thisMonth, locale),
        bestThisMonth = record(summary.bestDayThisMonth),
        bestThisYear = record(summary.bestDayThisYear),
    )
}
