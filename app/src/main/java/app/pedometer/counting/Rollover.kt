package app.pedometer.counting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
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

/** Fired by the rollover alarm; asks the running service to flush the step sensor. */
class RolloverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val flush = Intent(context, StepCounterService::class.java)
            .setAction(StepCounterService.ACTION_FLUSH)
        try {
            context.startService(flush)
        } catch (e: IllegalStateException) {
            // The service is not running and the app is in the background, so Android
            // refuses the start. Counting resumes the next time the app is opened.
        }
    }
}
