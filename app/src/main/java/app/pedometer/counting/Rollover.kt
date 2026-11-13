package app.pedometer.counting

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pedometer.PedometerApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZonedDateTime

private val ROLLOVER_TIME: LocalTime = LocalTime.of(23, 48)

/**
 * How long after the trigger Android may deliver the rollover alarm. Ten minutes is the
 * shortest window Android honours for a non-exact alarm, so delivery falls between 23:48
 * and 23:58.
 */
const val ROLLOVER_WINDOW_MS = 10 * 60 * 1000L

/**
 * The earliest time the next day-rollover alarm may fire: 23:48 today, or 23:48 tomorrow
 * once that has passed. Firing shortly before midnight lets steps walked late in the day be
 * recorded on the day that is ending.
 */
fun nextRolloverTrigger(now: ZonedDateTime): ZonedDateTime {
    val today = now.with(ROLLOVER_TIME)
    return if (now.isBefore(today)) today else today.plusDays(1)
}

/** Schedules (or reschedules) the day-rollover alarm for [RolloverReceiver]. */
fun scheduleRolloverAlarm(context: Context) {
    val trigger = nextRolloverTrigger(ZonedDateTime.now())
    val alarm = PendingIntent.getBroadcast(
        context,
        0,
        Intent(context, RolloverReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    // A windowed alarm is delivered inside its window. An allow-while-idle alarm would also
    // fire in deep sleep, but Android may deliver it up to an hour late, which would push an
    // evening's steps past midnight. A phone in deep sleep is lying still, so there is
    // nothing to flush then anyway.
    context.getSystemService(AlarmManager::class.java).setWindow(
        AlarmManager.RTC_WAKEUP,
        trigger.toInstant().toEpochMilli(),
        ROLLOVER_WINDOW_MS,
        alarm,
    )
}

/**
 * Fired by the rollover alarm shortly before midnight, so steps walked late in the day are
 * recorded on the day that is ending.
 */
class RolloverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as PedometerApp
        if (!StepCounterService.canCount(app)) return
        // Keeps the broadcast (and the alarm's wake lock) alive while the work finishes.
        val pending = goAsync()
        app.appScope.launch {
            try {
                if (app.repository.showStepCount.first()) {
                    flushService(app)
                } else {
                    app.recorder.recordOnce(ROLLOVER_READ_TIMEOUT_MS)
                    app.effects.refreshNow()
                    scheduleRolloverAlarm(app)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Day rollover failed", e)
            } finally {
                pending.finish()
            }
        }
    }

    /** Continuous mode: the running service flushes the sensor and reschedules the alarm. */
    private fun flushService(context: Context) {
        val flush = Intent(context, StepCounterService::class.java)
            .setAction(StepCounterService.ACTION_FLUSH)
        try {
            context.startService(flush)
        } catch (e: IllegalStateException) {
            // The service is not running and the app is in the background, so Android
            // refuses the start. Counting resumes the next time the app is opened.
        }
    }

    private companion object {
        const val TAG = "RolloverReceiver"

        // Well inside the ten seconds Android gives a broadcast receiver.
        const val ROLLOVER_READ_TIMEOUT_MS = 5_000L
    }
}
