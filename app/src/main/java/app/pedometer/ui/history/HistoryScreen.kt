package app.pedometer.ui.history

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pedometer.R

@Composable
fun HistoryScreen(viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val months = state ?: return

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = stringResource(R.string.tab_history),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 8.dp),
        )
        if (months.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = 16.dp)) {
                months.forEach { month ->
                    item(key = month.title) {
                        Text(
                            text = month.title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
                        )
                    }
                    items(count = month.rows.size, key = { "${month.title}/${month.rows[it].label}" }) { index ->
                        DayRow(month.rows[index])
                        if (index < month.rows.lastIndex) HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun DayRow(row: HistoryRow) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = row.label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = row.stepsText, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.width(8.dp))
        // The slot is always reserved so the step counts stay aligned in a column.
        Box(modifier = Modifier.size(20.dp)) {
            if (row.goalReached) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = stringResource(R.string.history_goal_reached),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}
