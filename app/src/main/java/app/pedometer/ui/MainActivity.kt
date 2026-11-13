package app.pedometer.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.pedometer.BuildConfig
import app.pedometer.PedometerApp
import app.pedometer.counting.StepCounterService
import app.pedometer.ui.theme.PedometerPalTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val app get() = application as PedometerApp

    // While the app is visible, keep "today" correct across midnight and clock changes.
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            app.todaySource.refresh()
        }
    }

    // Listens to the step sensor while the app is on screen, so the count is live even in
    // periodic mode, where nothing else is listening.
    private var liveCounting: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Creates the app's effects now, so goal and settings changes refresh the widget and
        // the goal alert even before any step has been recorded in this process.
        app.effects
        // Debug builds can skip the sensor check so the screens can be viewed on an emulator.
        val skipSensorCheck = BuildConfig.DEBUG && intent.getBooleanExtra(EXTRA_SKIP_SENSOR_CHECK, false)
        setContent {
            PedometerPalTheme {
                AppRoot(skipSensorCheck)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIME_TICK)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        ContextCompat.registerReceiver(this, clockReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onResume() {
        super.onResume()
        app.todaySource.refresh()
        // Re-applies the counting mode: restarts the service if the system stopped it, or
        // makes sure the catch-up job is scheduled.
        lifecycleScope.launch { app.countingController.apply() }
        // Started here rather than in onStart because activity access may just have been
        // granted in a system dialog, which pauses but does not stop the activity.
        if (liveCounting?.isActive != true && StepCounterService.canCount(this)) {
            liveCounting = lifecycleScope.launch { app.recorder.recorded.collect { } }
        }
    }

    override fun onStop() {
        liveCounting?.cancel()
        liveCounting = null
        unregisterReceiver(clockReceiver)
        super.onStop()
    }

    private companion object {
        const val EXTRA_SKIP_SENSOR_CHECK = "skip_sensor_check"
    }
}
