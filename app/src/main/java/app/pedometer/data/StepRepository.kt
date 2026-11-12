package app.pedometer.data

import app.pedometer.core.DayCount
import app.pedometer.core.Goal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.datetime.LocalDate

/** The single entry point the UI and the counting service use for step data and settings. */
class StepRepository(
    private val dao: StepDao,
    private val settings: SettingsStore,
) {
    /** Every recorded day, oldest first. */
    val days: Flow<List<DayCount>> = dao.observeDays().map { rows ->
        rows.map { DayCount(LocalDate.parse(it.date), it.steps) }
    }

    val goal: Flow<Int> = settings.goal

    val batteryPromptShown: Flow<Boolean> = settings.batteryPromptShown

    /** Applies a cumulative step sensor reading taken on [date]. */
    suspend fun recordReading(sensorValue: Long, bootCount: Int, date: LocalDate) {
        dao.record(sensorValue, bootCount, date.toString())
    }

    /** Adds steps directly, bypassing the sensor baseline. Used by the debug-only test receiver. */
    suspend fun addSteps(date: LocalDate, steps: Int) {
        dao.addSteps(date.toString(), steps)
    }

    suspend fun setGoal(goal: Int) {
        require(Goal.isValid(goal)) { "Goal must be from ${Goal.MIN} to ${Goal.MAX}, was $goal" }
        settings.setGoal(goal)
    }

    suspend fun markBatteryPromptShown() {
        settings.markBatteryPromptShown()
    }
}
