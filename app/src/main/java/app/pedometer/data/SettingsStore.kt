package app.pedometer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pedometer.core.Goal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import java.io.IOException

/** Small settings that are not step data: the daily goal and the one-time battery prompt flag. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    // An unreadable settings file falls back to defaults rather than crashing the app.
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val goal: Flow<Int> = preferences.map { it[GOAL]?.takeIf(Goal::isValid) ?: Goal.DEFAULT }

    val batteryPromptShown: Flow<Boolean> = preferences.map { it[BATTERY_PROMPT_SHOWN] ?: false }

    /** Whether the permanent step count notification (and with it continuous counting) is on. */
    val showStepCount: Flow<Boolean> = preferences.map { it[SHOW_STEP_COUNT] ?: true }

    val goalAlertEnabled: Flow<Boolean> = preferences.map { it[GOAL_ALERT_ENABLED] ?: false }

    /** The day the goal-reached notification was last sent; null if never, or if unreadable. */
    val lastGoalAlertDate: Flow<LocalDate?> = preferences.map { stored ->
        stored[LAST_GOAL_ALERT_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    suspend fun setGoal(goal: Int) {
        dataStore.edit { it[GOAL] = goal }
    }

    suspend fun markBatteryPromptShown() {
        dataStore.edit { it[BATTERY_PROMPT_SHOWN] = true }
    }

    suspend fun setShowStepCount(show: Boolean) {
        dataStore.edit { it[SHOW_STEP_COUNT] = show }
    }

    suspend fun setGoalAlertEnabled(enabled: Boolean) {
        dataStore.edit { it[GOAL_ALERT_ENABLED] = enabled }
    }

    suspend fun markGoalAlertSent(date: LocalDate) {
        dataStore.edit { it[LAST_GOAL_ALERT_DATE] = date.toString() }
    }

    private companion object {
        val GOAL = intPreferencesKey("goal")
        val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")
        val SHOW_STEP_COUNT = booleanPreferencesKey("show_step_count")
        val GOAL_ALERT_ENABLED = booleanPreferencesKey("goal_alert_enabled")
        val LAST_GOAL_ALERT_DATE = stringPreferencesKey("last_goal_alert_date")
    }
}
