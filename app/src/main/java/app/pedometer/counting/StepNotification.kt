package app.pedometer.counting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import app.pedometer.R
import app.pedometer.ui.MainActivity
import app.pedometer.ui.formatSteps
import java.util.Locale

/** The permanent, silent notification that shows today's steps while the service runs. */
object StepNotification {
    const val NOTIFICATION_ID = 1
    private const val CHANNEL_ID = "steps"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, steps: Int, goal: Int): Notification {
        val locale = Locale.getDefault()
        // The same intent the launcher icon sends, so a tap brings the open app to the
        // front instead of stacking a second copy of the screen on top of it.
        val launch = Intent(context, MainActivity::class.java)
            .setAction(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LAUNCHER)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        val openApp = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(context.getString(R.string.notification_title, formatSteps(steps, locale)))
            .setContentText(context.getString(R.string.notification_text, formatSteps(goal, locale)))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }
}
