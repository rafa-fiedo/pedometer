package app.pedometer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pedometer.core.BodySize
import app.pedometer.core.Goal
import app.pedometer.core.WeekStart
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate
import java.io.IOException

/** Small settings that are not step data: the goal, notification switches, body size and week start. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    // An unreadable settings file falls back to defaults rather than crashing the app.
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val goal: Flow<Int> = preferences.map { it[GOAL]?.takeIf(Goal::isValid) ?: Goal.DEFAULT }

    val batteryPromptShown: Flow<Boolean> = preferences.map { it[BATTERY_PROMPT_SHOWN] ?: false }

    /**
     * Whether the permanent step count notification (and with it continuous counting) is on.
     * Off on a fresh install: steps are caught up periodically instead.
     */
    val showStepCount: Flow<Boolean> = preferences.map { it[SHOW_STEP_COUNT] ?: false }

    /** On by default: a fresh install says when the goal is reached and stays quiet otherwise. */
    val goalAlertEnabled: Flow<Boolean> = preferences.map { it[GOAL_ALERT_ENABLED] ?: true }

    /** The day the goal-reached notification was last sent; null if never, or if unreadable. */
    val lastGoalAlertDate: Flow<LocalDate?> = preferences.map { stored ->
        stored[LAST_GOAL_ALERT_DATE]?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
    }

    /** Null when not set, or when the stored value is outside the allowed range. */
    val heightCm: Flow<Int?> = preferences.map { it[HEIGHT_CM]?.takeIf { cm -> cm in BodySize.HEIGHT_RANGE } }

    /** Null when not set, or when the stored value is outside the allowed range. */
    val weightKg: Flow<Int?> = preferences.map { it[WEIGHT_KG]?.takeIf { kg -> kg in BodySize.WEIGHT_RANGE } }

    /** Phone default when not set or unreadable. */
    val weekStart: Flow<WeekStart> = preferences.map { stored ->
        WeekStart.entries.firstOrNull { it.name == stored[WEEK_START] } ?: WeekStart.PHONE_DEFAULT
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

    /** Null clears the height. */
    suspend fun setHeightCm(value: Int?) {
        dataStore.edit { if (value == null) it.remove(HEIGHT_CM) else it[HEIGHT_CM] = value }
    }

    /** Null clears the weight. */
    suspend fun setWeightKg(value: Int?) {
        dataStore.edit { if (value == null) it.remove(WEIGHT_KG) else it[WEIGHT_KG] = value }
    }

    suspend fun setWeekStart(value: WeekStart) {
        dataStore.edit { it[WEEK_START] = value.name }
    }

    private companion object {
        val GOAL = intPreferencesKey("goal")
        val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")
        val SHOW_STEP_COUNT = booleanPreferencesKey("show_step_count")
        val GOAL_ALERT_ENABLED = booleanPreferencesKey("goal_alert_enabled")
        val LAST_GOAL_ALERT_DATE = stringPreferencesKey("last_goal_alert_date")
        val HEIGHT_CM = intPreferencesKey("height_cm")
        val WEIGHT_KG = intPreferencesKey("weight_kg")
        val WEEK_START = stringPreferencesKey("week_start")
    }
}
