package app.pedometer.ui.onboarding

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pedometer.R

fun hasActivityPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
        PackageManager.PERMISSION_GRANTED

private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * First-run screen. Asks for physical-activity access, then (Android 13+) for notifications,
 * and calls [onGranted] once activity access is in place.
 */
@Composable
fun PermissionScreen(onGranted: () -> Unit) {
    val context = LocalContext.current
    var refused by rememberSaveable { mutableStateOf(false) }
    var mustOpenSettings by rememberSaveable { mutableStateOf(false) }
    // True while a system permission dialog is on screen, so returning from the first dialog
    // does not leave this screen before the second one has been asked.
    var requesting by rememberSaveable { mutableStateOf(false) }
    // Notifications are asked for once per visit to this screen, whichever way activity
    // access was granted.
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        // Counting works with or without notifications, so either answer continues.
        requesting = false
        onGranted()
    }
    // Asks for notifications if that is still needed; otherwise the screen is finished.
    val continueAfterActivityAccess = {
        if (needsNotificationPermission(context) && !askedForNotifications) {
            askedForNotifications = true
            requesting = true
            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            requesting = false
            onGranted()
        }
    }
    val activityLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            continueAfterActivityAccess()
        } else {
            requesting = false
            refused = true
            // After a refusal Android stops showing the dialog once it reports no
            // rationale; from then on only the system settings can grant access.
            val activity = context.findActivity()
            mustOpenSettings = activity == null ||
                !ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACTIVITY_RECOGNITION,
                )
        }
    }

    // Covers access granted from the system settings while this screen was in the background.
    LifecycleResumeEffect(Unit) {
        if (!requesting && hasActivityPermission(context)) continueAfterActivityAccess()
        onPauseOrDispose { }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.DirectionsWalk,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.permission_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (refused) R.string.permission_refused_body else R.string.permission_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                if (mustOpenSettings) {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                } else {
                    requesting = true
                    activityLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
                }
            },
        ) {
            Text(
                stringResource(
                    if (mustOpenSettings) R.string.permission_open_settings else R.string.permission_allow,
                ),
            )
        }
    }
}
