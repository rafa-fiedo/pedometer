package app.pedometer

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn

/** Today's date according to the phone's clock and time zone. */
fun currentLocalDate(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())

/**
 * Holds the date the screens treat as "today". It only changes when [refresh] is called,
 * which the activity does on resume and on clock ticks, so every screen rolls over together.
 */
class TodaySource {
    private val _today = MutableStateFlow(currentLocalDate())
    val today: StateFlow<LocalDate> = _today.asStateFlow()

    fun refresh() {
        _today.value = currentLocalDate()
    }
}
