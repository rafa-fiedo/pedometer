package app.pedometer.counting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Restarts step counting after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            StepCounterService.start(context)
        }
    }
}
