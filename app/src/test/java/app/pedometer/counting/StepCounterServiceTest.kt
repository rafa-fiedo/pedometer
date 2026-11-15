package app.pedometer.counting

import android.Manifest
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorManager
import androidx.test.core.app.ApplicationProvider
import app.pedometer.PedometerApp
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Switching the step count off stops the service through a stop request it handles itself.
 * Being stopped from outside right after being started would crash the app, because Android
 * requires a service started that way to reach the foreground first; a request is delivered
 * after the start, so the service is always in the foreground by the time it stops.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepCounterServiceTest {
    private val app: PedometerApp = ApplicationProvider.getApplicationContext()
    private val stopRequest
        get() = Intent(app, StepCounterService::class.java).setAction(StepCounterService.ACTION_STOP)

    @Before
    fun setUp() {
        shadowOf(app.getSystemService(SensorManager::class.java))
            .addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    @Test
    fun aStopRequestEndsARunningService() {
        val controller = Robolectric.buildService(StepCounterService::class.java).create().startCommand(0, 1)
        assertFalse(shadowOf(controller.get()).isStoppedBySelf)

        controller.withIntent(stopRequest).startCommand(0, 2)

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        controller.destroy()
    }

    @Test
    fun aStopRequestAloneNeverShowsTheNotification() {
        val controller = Robolectric.buildService(StepCounterService::class.java, stopRequest)
            .create()
            .startCommand(0, 1)

        assertTrue(shadowOf(controller.get()).isStoppedBySelf)
        assertNull(shadowOf(controller.get()).lastForegroundNotification)
        controller.destroy()
    }
}
