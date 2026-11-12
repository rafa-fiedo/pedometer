package app.pedometer.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pedometer.PedometerApp
import app.pedometer.TodaySource
import app.pedometer.core.Goal
import app.pedometer.data.StepRepository
import app.pedometer.ui.firstDayOfWeek
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class TodayViewModel(
    private val repository: StepRepository,
    todaySource: TodaySource,
) : ViewModel() {
    /** Null until the first values have loaded. */
    val state: StateFlow<TodayUiState?> =
        combine(repository.days, repository.goal, todaySource.today) { days, goal, today ->
            val locale = Locale.getDefault()
            todayUiState(days, goal, today, firstDayOfWeek(locale), locale)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGoal(goal: Int) {
        if (!Goal.isValid(goal)) return
        viewModelScope.launch { repository.setGoal(goal) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PedometerApp
                TodayViewModel(app.repository, app.todaySource)
            }
        }
    }
}
