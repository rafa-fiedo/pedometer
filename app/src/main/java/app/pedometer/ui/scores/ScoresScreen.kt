package app.pedometer.ui.scores

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.pedometer.R
import app.pedometer.core.ChartRange
import kotlinx.datetime.LocalDate

@Composable
fun ScoresScreen(viewModel: ScoresViewModel = viewModel(factory = ScoresViewModel.Factory)) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state ?: return
    ScoresContent(
        current,
        onRange = viewModel::selectRange,
        onPrevious = viewModel::previous,
        onNext = viewModel::next,
        onSelect = viewModel::select,
    )
}

/** The chart with its controls, then the records. */
@Composable
fun ScoresContent(
    state: ScoresUiState,
    onRange: (ChartRange) -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelect: (LocalDate?) -> Unit,
) {
    val chart = state.chart
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        Text(text = stringResource(R.string.tab_scores), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))

        RangeSwitch(chart.range, onRange)

        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(onClick = onPrevious, enabled = chart.canGoBack) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.chart_previous),
                )
            }
            Text(
                text = chart.periodLabel,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onNext, enabled = chart.canGoForward) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = stringResource(R.string.chart_next),
                )
            }
        }

        StepChartView(
            chart = chart,
            description = stringResource(
                R.string.chart_description, chart.periodLabel, chart.totalText, chart.averageText,
            ),
            onSelect = onSelect,
            modifier = Modifier.padding(top = 8.dp),
        )

        val selected = chart.selected
        Text(
            text = if (selected != null) {
                val kcal = selected.kcalText
                if (kcal != null) {
                    stringResource(R.string.chart_selected_day_kcal, selected.dateText, selected.stepsText, kcal)
                } else {
                    stringResource(R.string.chart_selected_day, selected.dateText, selected.stepsText)
                }
            } else {
                val kcal = chart.kcalText
                if (kcal != null) {
                    stringResource(R.string.chart_summary_kcal, chart.totalText, chart.averageText, kcal)
                } else {
                    stringResource(R.string.chart_summary, chart.totalText, chart.averageText)
                }
            },
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(top = 12.dp),
        )

        SectionTitle(stringResource(R.string.scores_records))
        RecordCard(stringResource(R.string.scores_best_month), state.bestThisMonth)
        Spacer(Modifier.height(12.dp))
        RecordCard(stringResource(R.string.scores_best_year), state.bestThisYear)
    }
}

@Composable
private fun RangeSwitch(range: ChartRange, onRange: (ChartRange) -> Unit) {
    val labels = listOf(
        ChartRange.WEEK to R.string.chart_week,
        ChartRange.MONTH to R.string.chart_month,
        ChartRange.YEAR to R.string.chart_year,
    )
    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
        labels.forEachIndexed { index, (option, label) ->
            SegmentedButton(
                selected = range == option,
                onClick = { onRange(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = labels.size),
            ) {
                Text(stringResource(label))
            }
        }
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
