package app.pedometer.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.pedometer.PedometerApp
import app.pedometer.currentLocalDate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/**
 * Debug builds only. Adds steps without walking, for checking the screens:
 *
 * `adb shell am broadcast -n app.pedometer/.debug.DebugStepsReceiver
 *     -a app.pedometer.DEBUG_ADD_STEPS --ei steps 500 [--es date 2026-11-07]`
 */
class DebugStepsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val steps = intent.getIntExtra("steps", -1)
        if (steps < 0) return
        val date = intent.getStringExtra("date")?.let(LocalDate::parse) ?: currentLocalDate()
        val repository = (context.applicationContext as PedometerApp).repository
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                repository.addSteps(date, steps)
            } finally {
                pending.finish()
            }
        }
    }
}
