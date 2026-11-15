package app.pedometer.ui.settings

import app.pedometer.ui.formatSteps
import java.util.Locale

/** Everything the Settings screen displays. */
data class SettingsUiState(
    val goalText: String,
    /** The raw goal, used to prefill the goal dialog. */
    val goal: Int,
    val showStepCount: Boolean,
    val goalAlertEnabled: Boolean,
    /** Whether to explain how counting works while the step count notification is off. */
    val showPeriodicNote: Boolean,
)

fun settingsUiState(
    goal: Int,
    showStepCount: Boolean,
    goalAlertEnabled: Boolean,
    locale: Locale,
): SettingsUiState = SettingsUiState(
    goalText = "${formatSteps(goal, locale)} steps",
    goal = goal,
    showStepCount = showStepCount,
    goalAlertEnabled = goalAlertEnabled,
    showPeriodicNote = !showStepCount,
)
