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
    private val controls = MutableStateFlow(ChartControls())

    /** Null until the first values have loaded. */
    val state: StateFlow<ScoresUiState?> =
        combine(
            repository.days, repository.goal, todaySource.today, controls, repository.weekStart,
        ) { days, goal, today, c, weekStart ->
            val locale = Locale.getDefault()
            scoresUiState(
                days, today, goal, weekStart.resolve(firstDayOfWeek(locale)), locale,
                c.range, c.currentPeriodsBack, c.selected,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun selectRange(range: ChartRange) = controls.update { it.withRange(range) }

    fun previous() = controls.update { it.back(canGoBack = state.value?.chart?.canGoBack == true) }

    fun next() = controls.update { it.forward(canGoForward = state.value?.chart?.canGoForward == true) }

    fun select(date: LocalDate?) = controls.update { it.withSelected(date) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PedometerApp
                ScoresViewModel(app.repository, app.todaySource)
            }
        }
    }
}
