package app.pedometer.counting

import android.Manifest
import android.app.AlarmManager
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import app.pedometer.PedometerApp
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** What switching between the counting modes actually does to the phone. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CountingModeSwitchTest {
    private val app: PedometerApp = ApplicationProvider.getApplicationContext()
    private val workManager: WorkManager get() = WorkManager.getInstance(app)
    private val alarms get() = shadowOf(app.getSystemService(AlarmManager::class.java))

    @Before
    fun setUp() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
        shadowOf(app.getSystemService(SensorManager::class.java))
            .addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    private fun work(name: String): List<WorkInfo> = workManager.getWorkInfosForUniqueWork(name).get()

    private fun isActive(info: WorkInfo) =
        info.state == WorkInfo.State.ENQUEUED || info.state == WorkInfo.State.RUNNING

    private fun startedServiceClass(): String? = shadowOf(app).nextStartedService?.component?.className

    @Test
    fun stepCountOffSchedulesCatchUpAndTheRolloverAlarm() = runBlocking {
        app.repository.setShowStepCount(false)
        app.countingController.apply()

        assertTrue(work("step-catch-up").single().let(::isActive))
        assertEquals(1, work("step-catch-up-now").size)
        assertTrue(alarms.scheduledAlarms.isNotEmpty())
        // A running step count service is asked to stop itself; nothing is started in the
        // foreground.
        val sent = shadowOf(app).nextStartedService
        assertEquals(StepCounterService::class.java.name, sent?.component?.className)
        assertEquals(StepCounterService.ACTION_STOP, sent?.action)
        assertNull(startedServiceClass())
    }

    @Test
    fun reapplyingKeepsTheSamePeriodicSchedule() = runBlocking {
        app.repository.setShowStepCount(false)
        app.countingController.apply()
        val first = work("step-catch-up").single().id

        app.countingController.apply()

        assertEquals(first, work("step-catch-up").single().id)
    }

    @Test
    fun stepCountOnStartsTheServiceAndCancelsCatchUp() = runBlocking {
        app.repository.setShowStepCount(false)
        app.countingController.apply()
        app.repository.setShowStepCount(true)
        app.countingController.apply()

        assertEquals(StepCounterService::class.java.name, startedServiceClass())
        assertTrue(work("step-catch-up").none(::isActive))
        assertTrue(work("step-catch-up-now").none(::isActive))
    }

    @Test
    fun withoutActivityAccessNothingCounts() = runBlocking {
        shadowOf(app).denyPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        app.repository.setShowStepCount(true)
        app.countingController.apply()

        assertTrue(work("step-catch-up").none(::isActive))
        assertTrue(alarms.scheduledAlarms.isEmpty())
        // The only thing sent is a request for any running service to stop itself.
        assertEquals(StepCounterService.ACTION_STOP, shadowOf(app).nextStartedService?.action)
        assertNull(startedServiceClass())
    }

    @Test
    fun rolloverWithTheStepCountOnAsksTheServiceToFlush() = runBlocking {
        app.repository.setShowStepCount(true)
        app.sendBroadcast(Intent(app, RolloverReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()

        waitUntil { shadowOf(app).peekNextStartedService() != null }
        assertEquals(StepCounterService.ACTION_FLUSH, shadowOf(app).nextStartedService.action)
    }

    @Test
    fun rolloverWithTheStepCountOffReschedulesItself() = runBlocking {
        app.repository.setShowStepCount(false)
        app.sendBroadcast(Intent(app, RolloverReceiver::class.java))
        shadowOf(Looper.getMainLooper()).idle()

        // The receiver waits up to 5 seconds for a sensor reading before rescheduling.
        waitUntil(timeoutMs = 10_000) { alarms.scheduledAlarms.isNotEmpty() }
        assertNull(startedServiceClass())
    }

    private fun waitUntil(timeoutMs: Long = 3_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "Condition not met within $timeoutMs ms" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
    }
}
