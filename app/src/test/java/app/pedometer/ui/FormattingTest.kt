package app.pedometer.ui

import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class FormattingTest {
    private val date = LocalDate.parse("2026-11-10")

    @Test
    fun steps() {
        assertEquals("6,482", formatSteps(6_482, Locale.US))
        assertEquals("0", formatSteps(0, Locale.US))
    }

    @Test
    fun dates() {
        assertEquals("Tuesday, 10 November", formatLongDate(date, Locale.US))
        assertEquals("Tue 10", formatDayRow(date, Locale.US))
        assertEquals("November 2026", formatMonthTitle(date, Locale.US))
        assertEquals("Sat 7 November", formatRecordDate(LocalDate.parse("2026-11-07"), Locale.US))
    }
}
