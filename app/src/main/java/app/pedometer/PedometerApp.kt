package app.pedometer

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import app.pedometer.data.AppDatabase
import app.pedometer.data.SettingsStore
import app.pedometer.data.StepRepository

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** Owns the app-wide singletons; screens and the service reach them through the application. */
class PedometerApp : Application() {
    val repository: StepRepository by lazy {
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "pedometer.db").build()
        StepRepository(database.stepDao(), SettingsStore(settingsDataStore))
    }

    val todaySource = TodaySource()
}
