package app.pedometer.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import app.pedometer.core.Goal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Small settings that are not step data: the daily goal and the one-time battery prompt flag. */
class SettingsStore(private val dataStore: DataStore<Preferences>) {
    // An unreadable settings file falls back to defaults rather than crashing the app.
    private val preferences: Flow<Preferences> = dataStore.data.catch { error ->
        if (error is IOException) emit(emptyPreferences()) else throw error
    }

    val goal: Flow<Int> = preferences.map { it[GOAL]?.takeIf(Goal::isValid) ?: Goal.DEFAULT }

    val batteryPromptShown: Flow<Boolean> = preferences.map { it[BATTERY_PROMPT_SHOWN] ?: false }

    suspend fun setGoal(goal: Int) {
        dataStore.edit { it[GOAL] = goal }
    }

    suspend fun markBatteryPromptShown() {
        dataStore.edit { it[BATTERY_PROMPT_SHOWN] = true }
    }

    private companion object {
        val GOAL = intPreferencesKey("goal")
        val BATTERY_PROMPT_SHOWN = booleanPreferencesKey("battery_prompt_shown")
    }
}
