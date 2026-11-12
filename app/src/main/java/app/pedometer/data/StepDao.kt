package app.pedometer.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import app.pedometer.core.AccumulatorState
import app.pedometer.core.StepAccumulator
import kotlinx.coroutines.flow.Flow

@Dao
abstract class StepDao {
    @Query("SELECT * FROM daily_steps ORDER BY date")
    abstract fun observeDays(): Flow<List<DailyStepsEntity>>

    @Query("SELECT * FROM counter_state WHERE id = 0")
    abstract suspend fun counterState(): CounterStateEntity?

    @Query("INSERT OR IGNORE INTO daily_steps(date, steps) VALUES(:date, 0)")
    abstract suspend fun ensureDay(date: String)

    @Query("UPDATE daily_steps SET steps = steps + :steps WHERE date = :date")
    abstract suspend fun incrementDay(date: String, steps: Int)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun saveCounterState(state: CounterStateEntity)

    // Two statements rather than an upsert: SQLite on Android 10 (3.22) has no
    // `ON CONFLICT ... DO UPDATE`.
    @Transaction
    open suspend fun addSteps(date: String, steps: Int) {
        ensureDay(date)
        incrementDay(date, steps)
    }

    /**
     * Applies one step sensor reading: adds the new steps to [date] and saves the sensor
     * baseline in the same transaction, so a crash can neither double-count nor lose steps.
     */
    @Transaction
    open suspend fun record(sensorValue: Long, bootCount: Int, date: String) {
        val previous = counterState()?.let { AccumulatorState(it.lastSensorValue, it.lastBootCount) }
        val result = StepAccumulator.apply(previous, sensorValue, bootCount)
        addSteps(date, result.stepsToAdd)
        saveCounterState(
            CounterStateEntity(
                lastSensorValue = result.state.lastSensorValue,
                lastBootCount = result.state.lastBootCount,
            ),
        )
    }
}
