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
import org.robolectric.shadows.ShadowSystemClock
import android.os.SystemClock
import java.time.Duration
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

    // A reading the sensor is holding back until it is flushed, as Samsung phones do.
    private var heldBack: Long? = null
    private var flushes = 0

    // Readings whose last step was before this time belong to the day before.
    private var midnightMillis = Long.MIN_VALUE

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
            dateOf = { millis -> if (millis < midnightMillis) d("2026-11-11") else d("2026-11-12") },
            onRecorded = { recordedCalls++ },
            requestFlush = {
                flushes++
                heldBack?.let {
                    heldBack = null
                    send(it)
                }
            },
        )
    }

    @After
    fun tearDown() {
        recorderScope.cancel()
        db.close()
    }

    /** Delivers one sensor reading whose last step was [stepAgo] ago. */
    private fun send(value: Long, stepAgo: Duration = Duration.ZERO) {
        val event = SensorEventBuilder.newBuilder()
            .setSensor(sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)!!)
            .setValues(floatArrayOf(value.toFloat()))
            .setTimestamp(SystemClock.elapsedRealtimeNanos() - stepAgo.toNanos())
            .build()
        shadowOf(sensorManager).sendSensorEventToListeners(event)
    }

    /** Sends one sensor reading and waits until the recorder has written it. */
    private suspend fun emit(value: Long, stepAgo: Duration = Duration.ZERO) = coroutineScope {
        val written = async(start = CoroutineStart.UNDISPATCHED) { recorder.recorded.first() }
        send(value, stepAgo)
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

    @Test
    fun aReadingHeldBackPastMidnightCountsOnTheDayItsStepsWereWalked() = runBlocking {
        // The phone has been on for a while, so timestamps hours in the past are valid.
        ShadowSystemClock.advanceBy(Duration.ofHours(12))
        midnightMillis = System.currentTimeMillis() - Duration.ofHours(2).toMillis()
        val live = recorderScope.launch { recorder.recorded.collect {} }
        emit(100, stepAgo = Duration.ofHours(5))
        // 700 evening steps, delivered only after midnight.
        emit(800, stepAgo = Duration.ofHours(3))
        emit(850)

        assertEquals(700, steps("2026-11-11"))
        assertEquals(50, steps("2026-11-12"))
        live.cancel()
    }

    @Test
    fun recordOnceFlushesStepsTheSensorIsHoldingBack() = runBlocking {
        // The service already listens, so registering again brings no fresh reading: only a
        // flush makes the sensor hand over what it is holding.
        val live = recorderScope.launch { recorder.recorded.collect {} }
        emit(100)
        heldBack = 130

        assertTrue(recorder.recordOnce(timeoutMs = 2_000))
        assertEquals(30, steps("2026-11-12"))
        assertEquals(1, flushes)
        live.cancel()
    }
}
