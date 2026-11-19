package app.pedometer.counting

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import java.util.concurrent.CopyOnWriteArraySet

/** One reading of the cumulative step sensor, and when its last step was taken. */
data class StepReading(val value: Long, val lastStepMillis: Long)

/** The one place that talks to the phone's cumulative step sensor. */
class StepSensorReader(context: Context) {
    private val sensorManager = context.getSystemService(SensorManager::class.java)
    private val activeListeners = CopyOnWriteArraySet<SensorEventListener>()

    private val sensor: Sensor? get() = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    val hasSensor: Boolean get() = sensor != null

    /**
     * The sensor's running step total. The sensor is registered while the flow is collected
     * and released when collection ends. Only the newest value is kept, because each reading
     * supersedes the last. Emits nothing on a phone without the sensor.
     */
    fun readings(): Flow<StepReading> = callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val takenAt = lastStepTimeMillis(
                    eventNanos = event.timestamp,
                    nowElapsedNanos = SystemClock.elapsedRealtimeNanos(),
                    nowMillis = System.currentTimeMillis(),
                )
                trySend(StepReading(value = event.values[0].toLong(), lastStepMillis = takenAt))
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        val stepSensor = sensor
        val registered = stepSensor != null &&
            sensorManager.registerListener(listener, stepSensor, SensorManager.SENSOR_DELAY_NORMAL)
        if (registered) {
            activeListeners += listener
        } else if (stepSensor != null) {
            Log.w(TAG, "The step sensor refused the listener")
        }
        awaitClose {
            if (registered) {
                sensorManager.unregisterListener(listener)
                activeListeners -= listener
            }
        }
    }.conflate()

    /** Asks the sensor to deliver any readings it is still holding back. */
    fun flush() {
        activeListeners.forEach { sensorManager.flush(it) }
    }

    private companion object {
        const val TAG = "StepSensorReader"
    }
}
