package app.pedometer.counting

import android.Manifest
import android.hardware.Sensor
import android.hardware.SensorManager
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import app.pedometer.PedometerApp
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowSensor
import kotlin.test.assertFalse

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepCounterServiceTest {
    /**
     * The service stops itself when the step count is switched off. Being stopped from outside
     * right after being started would crash the app, because Android requires a service started
     * this way to reach the foreground first.
     */
    @Test
    fun stopsItselfWhenTheStepCountIsSwitchedOff() = runBlocking {
        val app: PedometerApp = ApplicationProvider.getApplicationContext()
        shadowOf(app.getSystemService(SensorManager::class.java))
            .addSensor(ShadowSensor.newInstance(Sensor.TYPE_STEP_COUNTER))
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)

        val controller = Robolectric.buildService(StepCounterService::class.java).create().startCommand(0, 1)
        val service = shadowOf(controller.get())
        assertFalse(service.isStoppedBySelf)

        app.repository.setShowStepCount(false)

        val deadline = System.currentTimeMillis() + 5_000
        while (!service.isStoppedBySelf) {
            check(System.currentTimeMillis() < deadline) { "The service did not stop itself" }
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(20)
        }
        controller.destroy()
        Unit
    }
}
