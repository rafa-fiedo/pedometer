package app.pedometer.counting

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pedometer.data.AppDatabase
import app.pedometer.data.SettingsStore
import app.pedometer.data.StepRepository
import app.pedometer.testing.InMemoryPreferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// These tests run in real time, not on a virtual test clock: the database writes happen on
// real threads, and a virtual clock would fire `recordOnce`'s timeout before they finish.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepRecorderTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val sensorManager: SensorManager = context.getSystemService(SensorManager::class.java)
    private lateinit var db: AppDatabase
    private lateinit var repo: StepRepository
    private lateinit var recorder: StepRecorder
    private val recorderScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private var recordedCalls = 0

    private fun d(iso: String) = LocalDate.parse(iso)

    private suspend fun steps(date: String): Int =
        repo.days.first().firstOrNull { it.date == d(date) }?.steps ?: 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore = InMemoryPreferencesDataStore()
        repo = StepRepository(db.stepDao(), SettingsStore(dataStore))
        shadowOf(sensorManager).addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        recorder = StepRecorder(
            reader = StepSensorReader(context),
            repository = repo,
            scope = recorderScope,
            bootCount = { 1 },
            today = { d("2026-11-12") },
            onRecorded = { recordedCalls++ },
        )
    }

    @After
    fun tearDown() {
        recorderScope.cancel()
        db.close()
    }

    /** Sends one sensor reading and waits until the recorder has written it. */
    private suspend fun emit(value: Long) = coroutineScope {
        val written = async(start = CoroutineStart.UNDISPATCHED) { recorder.recorded.first() }
        val event = SensorEventBuilder.newBuilder()
            .setSensor(sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!)
            .setValues(floatArrayOf(value.toFloat()))
            .build()
        shadowOf(sensorManager).sendSensorEventToListeners(event)
        written.await()
    }

    @Test
    fun liveReadingsAreRecorded() = runBlocking {
        val live = recorderScope.launch { recorder.recorded.collect {} }
        emit(100)
        emit(130)
        assertEquals(30, steps("2026-11-12"))
        live.cancel()
    }

    @Test
    fun recordOnceTimesOutQuietlyWhenTheSensorIsSilent() = runBlocking {
        assertFalse(recorder.recordOnce(timeoutMs = 300))
        assertEquals(0, steps("2026-11-12"))
        assertTrue(shadowOf(sensorManager).listeners.isEmpty())

        // A quiet run leaves nothing behind: the next run still gets the next reading.
        val next = async(start = CoroutineStart.UNDISPATCHED) { recorder.recordOnce() }
        emit(100)
        assertTrue(next.await())
    }

    @Test
    fun recordOnceRecordsTheNextReading() = runBlocking {
        val once = async(start = CoroutineStart.UNDISPATCHED) { recorder.recordOnce() }
        emit(100)
        assertTrue(once.await())
        assertEquals(1, recordedCalls)
    }

    @Test
    fun twoReadersCountEachStepOnce() = runBlocking {
        val live = recorderScope.launch { recorder.recorded.collect {} }
        emit(100)
        val once = async(start = CoroutineStart.UNDISPATCHED) { recorder.recordOnce() }
        emit(130)
        once.await()
        emit(150)
        assertEquals(50, steps("2026-11-12"))
        live.cancel()
    }

    @Test
    fun baselineCarriesAcrossSeparateSessions() = runBlocking {
        val first = async(start = CoroutineStart.UNDISPATCHED) { recorder.recordOnce() }
        emit(100)
        first.await()

        val live = recorderScope.launch { recorder.recorded.collect {} }
        emit(130)
        live.cancel()

        val second = async(start = CoroutineStart.UNDISPATCHED) { recorder.recordOnce() }
        emit(150)
        second.await()

        assertEquals(50, steps("2026-11-12"))
    }

    @Test
    fun onRecordedRunsAfterEachWrite() = runBlocking {
        val live = recorderScope.launch { recorder.recorded.collect {} }
        emit(100)
        emit(130)
        assertEquals(2, recordedCalls)
        live.cancel()
    }
}
