package app.pedometer.counting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalTime
import java.time.ZonedDateTime

private val ROLLOVER_TIME: LocalTime = LocalTime.of(23, 58)

/**
 * The next time the day-rollover alarm should fire: 23:58 today, or 23:58 tomorrow once that
 * has passed. Firing shortly before midnight lets steps walked late in the day be recorded
 * on the day that is ending.
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
