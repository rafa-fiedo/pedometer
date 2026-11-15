package app.pedometer.counting

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import androidx.core.app.NotificationCompat
import app.pedometer.R
import app.pedometer.ui.appLaunchIntent
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
        val openApp = PendingIntent.getActivity(
            context,
            0,
            appLaunchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_footprints)
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
