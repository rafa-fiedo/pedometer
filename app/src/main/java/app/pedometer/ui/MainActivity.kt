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
import app.pedometer.BuildConfig
import app.pedometer.PedometerApp
import app.pedometer.counting.StepCounterService
import app.pedometer.ui.theme.PedometerPalTheme

class MainActivity : ComponentActivity() {
    private val todaySource get() = (application as PedometerApp).todaySource

    // While the app is visible, keep "today" correct across midnight and clock changes.
    private val clockReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            todaySource.refresh()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
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
        todaySource.refresh()
        // Restarts counting if the system stopped the service while the app was closed.
        StepCounterService.start(this)
    }

    override fun onStop() {
        unregisterReceiver(clockReceiver)
        super.onStop()
    }

    private companion object {
        const val EXTRA_SKIP_SENSOR_CHECK = "skip_sensor_check"
    }
}
