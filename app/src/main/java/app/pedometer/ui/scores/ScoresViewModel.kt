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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class ScoresViewModel(
    repository: StepRepository,
    todaySource: TodaySource,
) : ViewModel() {
    /** Null until the first values have loaded. */
    val state: StateFlow<ScoresUiState?> =
        combine(repository.days, repository.goal, todaySource.today) { days, goal, today ->
            val locale = Locale.getDefault()
            scoresUiState(days, today, goal, firstDayOfWeek(locale), locale, ChartRange.WEEK, 0, null)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PedometerApp
                ScoresViewModel(app.repository, app.todaySource)
            }
        }
    }
}
