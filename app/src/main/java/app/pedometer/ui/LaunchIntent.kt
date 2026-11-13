package app.pedometer.ui

import android.content.Context
import android.content.Intent

/**
 * The intent the launcher icon sends. Notifications and the widget use it too, so a tap
 * brings the already-open app to the front instead of stacking a second copy of the screen.
 */
fun appLaunchIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java)
        .setAction(Intent.ACTION_MAIN)
        .addCategory(Intent.CATEGORY_LAUNCHER)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
