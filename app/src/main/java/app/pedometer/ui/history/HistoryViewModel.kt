package app.pedometer.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pedometer.PedometerApp
import app.pedometer.TodaySource
import app.pedometer.data.StepRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.util.Locale

class HistoryViewModel(
    repository: StepRepository,
    todaySource: TodaySource,
) : ViewModel() {
    /** Null until the first values have loaded. */
    val state: StateFlow<List<HistoryMonth>?> =
        combine(repository.days, repository.goal, todaySource.today) { days, goal, today ->
            historyMonths(days, goal, today, Locale.getDefault())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PedometerApp
                HistoryViewModel(app.repository, app.todaySource)
            }
        }
    }
}
