package app.pedometer.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One day's step total; [date] is an ISO date such as `2026-11-10`. */
@Entity(tableName = "daily_steps")
data class DailyStepsEntity(
    @PrimaryKey val date: String,
    val steps: Int,
)

/** The single row (id 0) holding the last step sensor reading and the boot it came from. */
@Entity(tableName = "counter_state")
data class CounterStateEntity(
    @PrimaryKey val id: Int = 0,
    @ColumnInfo(name = "last_sensor_value") val lastSensorValue: Long,
    @ColumnInfo(name = "last_boot_count") val lastBootCount: Int,
)
