package app.pedometer.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import app.pedometer.R
import app.pedometer.core.WeekStart
import app.pedometer.ui.dayName
import kotlinx.datetime.DayOfWeek

/** Picks the first day of the week: the phone's default, Monday or Sunday. */
@Composable
fun WeekStartDialog(
    selected: WeekStart,
    phoneDefaultDay: String,
    onSelect: (WeekStart) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_week_start)) },
        text = {
            Column(modifier = Modifier.selectableGroup()) {
                WeekStart.entries.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .selectable(selected = option == selected, role = Role.RadioButton) { onSelect(option) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = option == selected, onClick = null)
                        Text(weekStartLabel(option, phoneDefaultDay), modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.goal_cancel)) }
        },
    )
}

/** "Phone default (Monday)", "Monday" or "Sunday", in the phone's language. */
@Composable
fun weekStartLabel(option: WeekStart, phoneDefaultDay: String): String {
    val locale = LocalConfiguration.current.locales[0]
    return when (option) {
        WeekStart.PHONE_DEFAULT -> stringResource(R.string.settings_week_phone_default, phoneDefaultDay)
        WeekStart.MONDAY -> dayName(DayOfWeek.MONDAY, locale)
        WeekStart.SUNDAY -> dayName(DayOfWeek.SUNDAY, locale)
    }
}
