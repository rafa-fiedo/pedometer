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

/** The once-a-day notification sent when today's steps reach the goal. */
object GoalNotification {
    const val NOTIFICATION_ID = 2

    // Its own channel, separate from the silent step count, so it can make a sound and the
    // owner can tune or mute it independently in the system settings.
    private const val CHANNEL_ID = "goal"

    fun ensureChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.goal_notification_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun build(context: Context, goal: Int): Notification {
        val openApp = PendingIntent.getActivity(
            context,
            0,
            appLaunchIntent(context),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_walk)
            .setContentTitle(context.getString(R.string.goal_notification_title))
            .setContentText(
                context.getString(R.string.goal_notification_text, formatSteps(goal, Locale.getDefault())),
            )
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .build()
    }
}
