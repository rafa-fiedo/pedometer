package app.pedometer.ui.scores

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pedometer.R

@Composable
fun ScoresScreen(viewModel: ScoresViewModel = viewModel(factory = ScoresViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text = stringResource(R.string.tab_scores), style = MaterialTheme.typography.titleLarge)

        SectionTitle(stringResource(R.string.scores_totals))
        TotalRow(stringResource(R.string.total_today), current.todayText)
        HorizontalDivider()
        TotalRow(stringResource(R.string.total_this_week), current.weekText)
        HorizontalDivider()
        TotalRow(stringResource(R.string.total_this_month), current.monthText)

        SectionTitle(stringResource(R.string.scores_records))
        RecordCard(stringResource(R.string.scores_best_month), current.bestThisMonth)
        Spacer(Modifier.height(12.dp))
        RecordCard(stringResource(R.string.scores_best_year), current.bestThisYear)
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun TotalRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun RecordCard(label: String, record: RecordUi?) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = record?.stepsText ?: stringResource(R.string.scores_no_record),
                style = MaterialTheme.typography.headlineSmall,
            )
            if (record != null) {
                Text(
                    text = record.dateText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
