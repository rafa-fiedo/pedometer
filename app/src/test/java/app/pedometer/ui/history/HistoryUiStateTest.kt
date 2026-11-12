package app.pedometer.ui.history

import app.pedometer.core.DayCount
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals

class HistoryUiStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun groupsByMonthNewestFirst() {
        val days = listOf(DayCount(d("2026-10-30"), 10_000), DayCount(d("2026-11-01"), 4_310))
        assertEquals(
            listOf(
                HistoryMonth(
                    "November 2026",
                    listOf(HistoryRow("Mon 2", "0", false), HistoryRow("Sun 1", "4,310", false)),
                ),
                HistoryMonth(
                    "October 2026",
                    listOf(HistoryRow("Sat 31", "0", false), HistoryRow("Fri 30", "10,000", true)),
                ),
            ),
            historyMonths(days, 10_000, d("2026-11-02"), Locale.US),
        )
    }

    @Test
    fun emptyWithoutRecords() =
        assertEquals(emptyList(), historyMonths(emptyList(), 10_000, d("2026-11-02"), Locale.US))
}
