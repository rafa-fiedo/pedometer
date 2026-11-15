package app.pedometer.ui.settings

import app.pedometer.core.WeekStart
import app.pedometer.ui.dayName
import app.pedometer.ui.firstDayOfWeek
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
    /** Null when not set. */
    val heightCm: Int?,
    /** Null when not set. */
    val weightKg: Int?,
    val weekStart: WeekStart,
    /** The name of the day the phone's region starts weeks on, for the "Phone default" choice. */
    val phoneDefaultDay: String,
)

fun settingsUiState(
    goal: Int,
    showStepCount: Boolean,
    goalAlertEnabled: Boolean,
    heightCm: Int?,
    weightKg: Int?,
    weekStart: WeekStart,
    locale: Locale,
): SettingsUiState = SettingsUiState(
    goalText = "${formatSteps(goal, locale)} steps",
    goal = goal,
    showStepCount = showStepCount,
    goalAlertEnabled = goalAlertEnabled,
    showPeriodicNote = !showStepCount,
    heightCm = heightCm,
    weightKg = weightKg,
    weekStart = weekStart,
    phoneDefaultDay = dayName(firstDayOfWeek(locale), locale),
)
