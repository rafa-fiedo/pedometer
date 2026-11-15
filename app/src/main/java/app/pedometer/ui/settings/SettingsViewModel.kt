package app.pedometer.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import app.pedometer.PedometerApp
import app.pedometer.core.BodySize
import app.pedometer.core.Goal
import app.pedometer.core.WeekStart
import app.pedometer.counting.CountingController
import app.pedometer.data.StepRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsViewModel(
    private val repository: StepRepository,
    private val controller: CountingController,
) : ViewModel() {
    /** Null until the first values have loaded. */
    val state: StateFlow<SettingsUiState?> =
        combine(
            combine(repository.goal, repository.showStepCount, repository.goalAlertEnabled, ::Triple),
            combine(repository.heightCm, repository.weightKg, repository.weekStart, ::Triple),
        ) { (goal, show, alert), (height, weight, weekStart) ->
            settingsUiState(goal, show, alert, height, weight, weekStart, Locale.getDefault())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setGoal(goal: Int) {
        if (!Goal.isValid(goal)) return
        viewModelScope.launch { repository.setGoal(goal) }
    }

    /** Saves the switch and moves the app into the matching counting mode straight away. */
    fun setShowStepCount(show: Boolean) {
        viewModelScope.launch {
            repository.setShowStepCount(show)
            controller.apply()
        }
    }

    fun setGoalAlertEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setGoalAlertEnabled(enabled) }
    }

    /** Null clears the height; values outside the allowed range are ignored. */
    fun setHeightCm(value: Int?) {
        if (value != null && value !in BodySize.HEIGHT_RANGE) return
        viewModelScope.launch { repository.setHeightCm(value) }
    }

    /** Null clears the weight; values outside the allowed range are ignored. */
    fun setWeightKg(value: Int?) {
        if (value != null && value !in BodySize.WEIGHT_RANGE) return
        viewModelScope.launch { repository.setWeightKg(value) }
    }

    fun setWeekStart(value: WeekStart) {
        viewModelScope.launch { repository.setWeekStart(value) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as PedometerApp
                SettingsViewModel(app.repository, app.countingController)
            }
        }
    }
}
