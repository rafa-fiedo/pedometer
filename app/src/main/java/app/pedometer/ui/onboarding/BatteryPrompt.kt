package app.pedometer.ui.onboarding

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.pedometer.R
import app.pedometer.data.StepRepository
import kotlinx.coroutines.launch

/**
 * One-time offer to exempt the app from battery optimisation, so phones that stop background
 * apps aggressively keep the step counter alive. Either answer means it is never shown again.
 */
@Composable
fun BatteryPrompt(repository: StepRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Starts as "shown" so the dialog does not flash before the saved flag has loaded.
    val alreadyShown by repository.batteryPromptShown.collectAsStateWithLifecycle(initialValue = true)
    val alreadyExempt = remember {
        context.getSystemService(PowerManager::class.java)
            .isIgnoringBatteryOptimizations(context.packageName)
    }
    if (alreadyShown || alreadyExempt) return

    val markShown: () -> Unit = { scope.launch { repository.markBatteryPromptShown() } }
    AlertDialog(
        onDismissRequest = markShown,
        title = { Text(stringResource(R.string.battery_title)) },
        text = { Text(stringResource(R.string.battery_body)) },
        confirmButton = {
            TextButton(
                onClick = {
                    markShown()
                    requestBatteryExemption(context)
                },
            ) { Text(stringResource(R.string.battery_allow)) }
        },
        dismissButton = {
            TextButton(onClick = markShown) { Text(stringResource(R.string.battery_not_now)) }
        },
    )
}

// The direct request is restricted on Google Play; acceptable for a personal build and
// flagged in the design spec for review before any store release.
@SuppressLint("BatteryLife")
private fun requestBatteryExemption(context: Context) {
    val request = Intent(
        Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.fromParts("package", context.packageName, null),
    )
    try {
        context.startActivity(request)
    } catch (e: ActivityNotFoundException) {
        // Some phones do not offer this screen; counting still works without the exemption.
    }
}
