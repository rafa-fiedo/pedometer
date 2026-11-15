package app.pedometer.counting

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import app.pedometer.PedometerApp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Resumes step counting, in whichever mode is set, after the phone reboots. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as PedometerApp
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.countingController.apply()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not resume counting after boot", e)
            } finally {
                pending.finish()
            }
        }
    }

    private companion object {
        const val TAG = "BootReceiver"
    }
}
