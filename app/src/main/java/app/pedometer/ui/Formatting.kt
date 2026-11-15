package app.pedometer.ui

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toJavaLocalDate
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** A step count with grouping separators, e.g. "6,482". */
fun formatSteps(steps: Int, locale: Locale): String =
    NumberFormat.getIntegerInstance(locale).format(steps)

/** "Tuesday, 10 November" */
fun formatLongDate(date: LocalDate, locale: Locale): String = format(date, "EEEE, d MMMM", locale)

/** "Tue 10" */
fun formatDayRow(date: LocalDate, locale: Locale): String = format(date, "EEE d", locale)

/** "November 2026" */
fun formatMonthTitle(date: LocalDate, locale: Locale): String = format(date, "MMMM yyyy", locale)

/** "Sat 7 November" */
fun formatRecordDate(date: LocalDate, locale: Locale): String = format(date, "EEE d MMMM", locale)

/** The day's full name, starting with a capital: "Monday". */
fun dayName(day: DayOfWeek, locale: Locale): String =
    day.getDisplayName(TextStyle.FULL, locale).replaceFirstChar { it.titlecase(locale) }

/** The day weeks start on in [locale]: Monday in most of Europe, Sunday in the US. */
fun firstDayOfWeek(locale: Locale): DayOfWeek = WeekFields.of(locale).firstDayOfWeek

private fun format(date: LocalDate, pattern: String, locale: Locale): String =
    DateTimeFormatter.ofPattern(pattern, locale).format(date.toJavaLocalDate())
