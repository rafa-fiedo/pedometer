package app.pedometer.counting

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.pedometer.PedometerApp
import app.pedometer.core.Goal
import app.pedometer.currentLocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.ZonedDateTime

/**
 * Counts steps all day. Runs as a foreground service so Android keeps it alive, reads the
 * phone's cumulative step sensor, and writes new steps to the repository under today's date.
 */
class StepCounterService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // The sensor reports a running total, so only the newest reading matters. One consumer
    // reads this channel, which keeps readings in order.
    private val readings = Channel<Long>(Channel.CONFLATED)

    // Bumped when the date, clock or time zone changes, so the notification recomputes
    // "today" even when no steps arrive.
    private val clockChanges = MutableStateFlow(0)

    @Volatile
    private var shown = Shown(steps = 0, goal = Goal.DEFAULT)
    private var counting = false

    private val sensorManager by lazy { getSystemService(SensorManager::class.java) }
    private val notificationManager by lazy { getSystemService(NotificationManager::class.java) }
    private val repository by lazy { (application as PedometerApp).repository }

    private val sensorListener = object : SensorEventListener {
        override fun onSensorChanged(event: SensorEvent) {
            readings.trySend(event.values[0].toLong())
        }

        override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
    }

    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            clockChanges.update { it + 1 }
            scheduleRollover()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!canCount(this) || !enterForeground()) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCountingOnce()
        if (intent?.action == ACTION_FLUSH) flush()
        return START_STICKY
    }

    /** Must run on every start: each `startForegroundService` call has to be answered. */
    private fun enterForeground(): Boolean {
        StepNotification.ensureChannel(this)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH
        } else {
            0
        }
        val current = shown
        return try {
            ServiceCompat.startForeground(
                this,
                StepNotification.NOTIFICATION_ID,
                StepNotification.build(this, current.steps, current.goal),
                type,
            )
            true
        } catch (e: IllegalStateException) {
            // Android refused a foreground start from the background.
            Log.w(TAG, "Could not enter the foreground", e)
            false
        } catch (e: SecurityException) {
            Log.w(TAG, "Missing permission for the step counting service", e)
            false
        }
    }

    private fun startCountingOnce() {
        if (counting) return
        counting = true

        val sensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)
        sensorManager.registerListener(sensorListener, sensor, SensorManager.SENSOR_DELAY_NORMAL)

        scope.launch {
            for (value in readings) {
                try {
                    val bootCount = Settings.Global.getInt(contentResolver, Settings.Global.BOOT_COUNT, 0)
                    repository.recordReading(value, bootCount, currentLocalDate())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // The baseline was not advanced, so these steps are picked up by the
                    // next reading instead of being lost.
                    Log.w(TAG, "Could not record a step reading", e)
                }
            }
        }

        scope.launch {
            combine(repository.days, repository.goal, clockChanges) { days, goal, _ ->
                val today = currentLocalDate()
                Shown(steps = days.firstOrNull { it.date == today }?.steps ?: 0, goal = goal)
            }
                .distinctUntilChanged()
                .conflate()
                .collect { state ->
                    shown = state
                    notificationManager.notify(
                        StepNotification.NOTIFICATION_ID,
                        StepNotification.build(this@StepCounterService, state.steps, state.goal),
                    )
                    // At most one update per interval; `conflate` keeps the newest value
                    // waiting, so the final count always arrives.
                    delay(NOTIFICATION_INTERVAL_MS)
                }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)

        scheduleRollover()
    }

    /** Pulls any steps the sensor is still holding, so they land on the day that is ending. */
    private fun flush() {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PedometerPal:rollover")
            .acquire(FLUSH_WAKE_LOCK_MS)
        sensorManager.flush(sensorListener)
        scheduleRollover()
    }

    private fun scheduleRollover() {
        val trigger = nextRolloverTrigger(ZonedDateTime.now())
        val alarm = PendingIntent.getBroadcast(
            this,
            0,
            Intent(this, RolloverReceiver::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        getSystemService(AlarmManager::class.java)
            .setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger.toInstant().toEpochMilli(), alarm)
    }

    override fun onDestroy() {
        if (counting) {
            sensorManager.unregisterListener(sensorListener)
            unregisterReceiver(clockReceiver)
        }
        scope.cancel()
        super.onDestroy()
    }

    private data class Shown(val steps: Int, val goal: Int)

    companion object {
        const val ACTION_FLUSH = "app.pedometer.action.FLUSH"

        private const val TAG = "StepCounterService"
        private const val NOTIFICATION_INTERVAL_MS = 5_000L
        private const val FLUSH_WAKE_LOCK_MS = 5_000L

        fun hasStepSensor(context: Context): Boolean =
            context.getSystemService(SensorManager::class.java)
                .getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null

        /** True when the phone has a step sensor and the app may read it. */
        fun canCount(context: Context): Boolean =
            hasStepSensor(context) &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
                PackageManager.PERMISSION_GRANTED

        /** Starts counting if possible; safe to call repeatedly. */
        fun start(context: Context) {
            if (!canCount(context)) return
            try {
                ContextCompat.startForegroundService(context, Intent(context, StepCounterService::class.java))
            } catch (e: IllegalStateException) {
                // Android does not allow a foreground service start from the background here.
                Log.w(TAG, "Could not start the step counting service", e)
            }
        }
    }
}
