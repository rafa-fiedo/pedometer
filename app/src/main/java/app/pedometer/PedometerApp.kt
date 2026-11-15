package app.pedometer

import android.app.Application
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.pedometer.counting.CountingController
import app.pedometer.counting.StepEffects
import app.pedometer.counting.StepRecorder
import app.pedometer.counting.StepSensorReader
import app.pedometer.data.AppDatabase
import app.pedometer.data.SettingsStore
import app.pedometer.data.StepRepository
import app.pedometer.widget.PedometerWidget
import androidx.glance.appwidget.updateAll
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/** Owns the app-wide singletons; screens, the service and background jobs reach them here. */
class PedometerApp : Application() {
    /** Lives as long as the process; used for work that must outlast any one screen. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The same file the app has always used (files/datastore/settings.preferences_pb). It
    // belongs to this application object rather than to a process-wide singleton, so each
    // JVM test, which gets its own application, also gets its own settings.
    private val settingsDataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(
            scope = CoroutineScope(appScope.coroutineContext + Dispatchers.IO),
            produceFile = { preferencesDataStoreFile("settings") },
        )
    }

    val repository: StepRepository by lazy {
        val database = Room.databaseBuilder(this, AppDatabase::class.java, "pedometer.db").build()
        StepRepository(database.stepDao(), SettingsStore(settingsDataStore))
    }

    val todaySource = TodaySource()

    val sensorReader: StepSensorReader by lazy { StepSensorReader(this) }

    val effects: StepEffects by lazy {
        StepEffects(this, repository, appScope, refreshWidget = { PedometerWidget().updateAll(this@PedometerApp) })
    }

    val recorder: StepRecorder by lazy {
        StepRecorder(
            reader = sensorReader,
            repository = repository,
            scope = appScope,
            // Changes on every reboot, which is how a restart of the sensor's count is detected.
            bootCount = { Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0) },
            onRecorded = effects::requestRefresh,
        )
    }

    val countingController: CountingController by lazy { CountingController(this, repository) }

    // Never called on a real device; in JVM tests it stops this app instance's background
    // work when the test ends.
    override fun onTerminate() {
        appScope.cancel()
        super.onTerminate()
    }
}
