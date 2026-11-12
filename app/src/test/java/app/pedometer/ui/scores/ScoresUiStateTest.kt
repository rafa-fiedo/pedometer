package app.pedometer.ui.scores

import app.pedometer.core.DayCount
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ScoresUiStateTest {
    private fun d(iso: String) = LocalDate.parse(iso)

    @Test
    fun mapsTotalsAndRecords() {
        val days = listOf(
            DayCount(d("2026-06-14"), 18_342),
            DayCount(d("2026-11-07"), 12_870),
            DayCount(d("2026-11-09"), 11_204),
            DayCount(d("2026-11-10"), 6_482),
        )
        assertEquals(
            ScoresUiState(
                "6,482",
                "17,686",
                "30,556",
                RecordUi("12,870", "Sat 7 November"),
                RecordUi("18,342", "Sun 14 June"),
            ),
            scoresUiState(days, d("2026-11-10"), DayOfWeek.MONDAY, Locale.US),
        )
    }

    @Test
    fun noRecordsYet() {
        val s = scoresUiState(emptyList(), d("2026-11-10"), DayOfWeek.MONDAY, Locale.US)
        assertEquals("0", s.todayText)
        assertNull(s.bestThisMonth)
        assertNull(s.bestThisYear)
    }
}
