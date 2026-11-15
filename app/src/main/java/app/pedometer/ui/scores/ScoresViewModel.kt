package app.pedometer.ui.scores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pedometer.PedometerApp
import app.pedometer.TodaySource
import app.pedometer.core.ChartRange
import app.pedometer.data.StepRepository
import app.pedometer.ui.firstDayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.datetime.LocalDate
import java.util.Locale

class ScoresViewModel(
    repository: StepRepository,
    todaySource: TodaySource,
) : ViewModel() {
    /** Which chart is shown, how far back each view is, and the selected day. Kept while the app is open. */
    private data class ChartControls(
        val range: ChartRange = ChartRange.WEEK,
        val periodsBack: Map<ChartRange, Int> = emptyMap(),
        val selected: LocalDate? = null,
    )

    private val controls = MutableStateFlow(ChartControls())

    /** Null until the first values have loaded. */
    val state: StateFlow<ScoresUiState?> =
        combine(repository.days, repository.goal, todaySource.today, controls) { days, goal, today, c ->
            val locale = Locale.getDefault()
            scoresUiState(
                days, today, goal, firstDayOfWeek(locale), locale,
                c.range, c.periodsBack[c.range] ?: 0, c.selected,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectRange(range: ChartRange) = controls.update { it.copy(range = range, selected = null) }

    fun previous() {
        if (state.value?.chart?.canGoBack == true) move(by = 1)
    }

    fun next() {
        if (state.value?.chart?.canGoForward == true) move(by = -1)
    }

    fun select(date: LocalDate?) = controls.update { it.copy(selected = date) }

    private fun move(by: Int) = controls.update {
        val back = ((it.periodsBack[it.range] ?: 0) + by).coerceAtLeast(0)
        it.copy(periodsBack = it.periodsBack + (it.range to back), selected = null)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PedometerApp
                ScoresViewModel(app.repository, app.todaySource)
            }
        }
    }
}
