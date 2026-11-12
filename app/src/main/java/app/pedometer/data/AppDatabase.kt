package app.pedometer.data

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [DailyStepsEntity::class, CounterStateEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun stepDao(): StepDao
}
