package app.pedometer.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pedometer.BuildConfig
import app.pedometer.R
import app.pedometer.core.BodySize
import app.pedometer.ui.onboarding.needsNotificationPermission
import app.pedometer.ui.today.GoalDialog

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    val context = LocalContext.current
    var editingGoal by rememberSaveable { mutableStateOf(false) }
    var editingHeight by rememberSaveable { mutableStateOf(false) }
    var editingWeight by rememberSaveable { mutableStateOf(false) }
    var editingWeekStart by rememberSaveable { mutableStateOf(false) }

    // The goal alert is only switched on once Android allows the app to notify; a refusal
    // leaves the switch off.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setGoalAlertEnabled(true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Text(
            text = stringResource(R.string.settings_title),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )

        SectionTitle(stringResource(R.string.settings_section_goal))
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { editingGoal = true }
                .padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Text(text = stringResource(R.string.settings_daily_goal), style = MaterialTheme.typography.bodyLarge)
            Text(
                text = current.goalText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SectionTitle(stringResource(R.string.settings_section_you))
        ValueRow(
            title = stringResource(R.string.settings_height),
            value = current.heightCm?.let { stringResource(R.string.settings_height_value, it) }
                ?: stringResource(R.string.settings_not_set),
            onClick = { editingHeight = true },
        )
        ValueRow(
            title = stringResource(R.string.settings_weight),
            value = current.weightKg?.let { stringResource(R.string.settings_weight_value, it) }
                ?: stringResource(R.string.settings_not_set),
            onClick = { editingWeight = true },
        )

        SectionTitle(stringResource(R.string.settings_section_week))
        ValueRow(
            title = stringResource(R.string.settings_week_start),
            value = weekStartLabel(current.weekStart, current.phoneDefaultDay),
            onClick = { editingWeekStart = true },
        )

        SectionTitle(stringResource(R.string.settings_section_notifications))
        SwitchRow(
            title = stringResource(R.string.settings_show_step_count),
            subtitle = stringResource(R.string.settings_show_step_count_subtitle),
            checked = current.showStepCount,
            onCheckedChange = viewModel::setShowStepCount,
        )
        SwitchRow(
            title = stringResource(R.string.settings_goal_alert),
            subtitle = stringResource(R.string.settings_goal_alert_subtitle),
            checked = current.goalAlertEnabled,
            onCheckedChange = { wanted ->
                if (wanted && needsNotificationPermission(context)) {
                    notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setGoalAlertEnabled(wanted)
                }
            },
        )
        if (current.showPeriodicNote) {
            Text(
                text = stringResource(R.string.settings_periodic_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        Text(
            text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp),
        )
    }

    if (editingGoal) {
        GoalDialog(
            initial = current.goal,
            onSave = {
                viewModel.setGoal(it)
                editingGoal = false
            },
            onDismiss = { editingGoal = false },
        )
    }
    if (editingHeight) {
        MeasureDialog(
            title = stringResource(R.string.settings_height),
            label = stringResource(R.string.settings_height_field),
            error = stringResource(R.string.settings_height_error),
            initial = current.heightCm,
            range = BodySize.HEIGHT_RANGE,
            onSave = {
                viewModel.setHeightCm(it)
                editingHeight = false
            },
            onDismiss = { editingHeight = false },
        )
    }
    if (editingWeight) {
        MeasureDialog(
            title = stringResource(R.string.settings_weight),
            label = stringResource(R.string.settings_weight_field),
            error = stringResource(R.string.settings_weight_error),
            initial = current.weightKg,
            range = BodySize.WEIGHT_RANGE,
            onSave = {
                viewModel.setWeightKg(it)
                editingWeight = false
            },
            onDismiss = { editingWeight = false },
        )
    }
    if (editingWeekStart) {
        WeekStartDialog(
            selected = current.weekStart,
            phoneDefaultDay = current.phoneDefaultDay,
            onSelect = {
                viewModel.setWeekStart(it)
                editingWeekStart = false
            },
            onDismiss = { editingWeekStart = false },
        )
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 4.dp),
    )
}

/** A row that toggles its switch wherever it is tapped. */
@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(16.dp))
        // The row handles the tap, so the switch itself is display-only.
        Switch(checked = checked, onCheckedChange = null)
    }
}
