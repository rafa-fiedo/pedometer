package app.pedometer.ui.scores

import app.pedometer.core.BodySize
import app.pedometer.core.ChartRange
import app.pedometer.core.DayCount
import app.pedometer.core.StepChart
import app.pedometer.core.StepStats
import app.pedometer.ui.formatRecordDate
import app.pedometer.ui.formatSteps
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import java.util.Locale

data class RecordUi(val stepsText: String, val dateText: String)

data class ScoresUiState(
    val chart: ChartUiState,
    /** Null when no day this month has any steps. */
    val bestThisMonth: RecordUi?,
    /** Null when no day this year has any steps. */
    val bestThisYear: RecordUi?,
)

/** [periodsBack] counts whole weeks, months or years before the current one. */
fun scoresUiState(
    days: List<DayCount>,
    today: LocalDate,
    goal: Int,
    firstDayOfWeek: DayOfWeek,
    locale: Locale,
    range: ChartRange,
    periodsBack: Int,
    selected: LocalDate?,
    body: BodySize?,
): ScoresUiState {
    val summary = StepStats.summarize(days, today, firstDayOfWeek)
    val period = StepChart.shift(StepChart.periodContaining(range, today, firstDayOfWeek), -periodsBack, firstDayOfWeek)
    fun record(day: DayCount?) =
        day?.let { RecordUi(formatSteps(it.steps, locale), formatRecordDate(it.date, locale)) }
    return ScoresUiState(
        chart = chartUiState(StepChart.chart(days, period, today, goal), selected, locale, body),
        bestThisMonth = record(summary.bestDayThisMonth),
        bestThisYear = record(summary.bestDayThisYear),
    )
}
