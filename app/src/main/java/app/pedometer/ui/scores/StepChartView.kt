package app.pedometer.ui.scores

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import app.pedometer.core.ChartRange
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/** Marks the chart's drawing area, which is also the area that takes taps. */
const val STEP_CHART_TAG = "step-chart"

/**
 * Bars for a week or month, a thin line for a year, and a dashed goal line. Tapping a day
 * selects it and marks it on the chart; tapping it again clears it. Future days can't be
 * selected.
 */
@Composable
fun StepChartView(
    chart: ChartUiState,
    description: String,
    onSelect: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val goalStyle = MaterialTheme.typography.labelSmall.copy(color = colors.onSurfaceVariant)
    // The tap handler is installed once; these always hold the latest values.
    val currentChart by rememberUpdatedState(chart)
    val currentOnSelect by rememberUpdatedState(onSelect)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .testTag(STEP_CHART_TAG)
                .semantics { contentDescription = description }
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val c = currentChart
                        if (c.bars.isEmpty()) return@detectTapGestures
                        val bar = c.bars[dayIndexAt(offset.x, size.width.toFloat(), c.bars.size)]
                        if (bar.steps == null) return@detectTapGestures
                        currentOnSelect(if (bar.date == c.selected?.date) null else bar.date)
                    }
                },
        ) {
            if (chart.bars.isEmpty() || chart.scaleMax <= 0) return@Canvas
            if (chart.range == ChartRange.YEAR) {
                drawYearLine(chart, colors.primary, selectedColor = colors.tertiary)
            } else {
                drawBars(chart, reached = colors.primary, other = colors.surfaceVariant, selectedColor = colors.tertiary)
            }
            drawGoalLine(chart, colors.onSurfaceVariant)
            val label = textMeasurer.measure(chart.goalText, goalStyle)
            val y = yFor(chart.goal, chart.scaleMax)
            drawText(
                label,
                topLeft = Offset(size.width - label.size.width, (y - label.size.height - 2.dp.toPx()).coerceAtLeast(0f)),
            )
        }
        AxisLabels(chart, goalStyle, Modifier.padding(top = 4.dp))
    }
}

private fun DrawScope.yFor(steps: Int, scaleMax: Int): Float =
    size.height * (1f - steps.toFloat() / scaleMax).coerceIn(0f, 1f)

private fun DrawScope.drawBars(chart: ChartUiState, reached: Color, other: Color, selectedColor: Color) {
    val slot = size.width / chart.bars.size
    val barWidth = slot * if (chart.range == ChartRange.WEEK) 0.6f else 0.7f
    val corner = minOf(barWidth / 3f, 6.dp.toPx())
    chart.bars.forEachIndexed { i, bar ->
        val steps = bar.steps ?: return@forEachIndexed
        if (steps <= 0) return@forEachIndexed
        val top = yFor(steps, chart.scaleMax)
        val left = i * slot + (slot - barWidth) / 2f
        val color = when {
            bar.date == chart.selected?.date -> selectedColor
            bar.reachedGoal -> reached
            else -> other
        }
        // Rounded at the top only: a rounded bar with its lower corners squared off.
        drawRoundRect(color, Offset(left, top), Size(barWidth, size.height - top), CornerRadius(corner, corner))
        val square = minOf(corner, (size.height - top) / 2f)
        drawRect(color, Offset(left, size.height - square), Size(barWidth, square))
    }
}

private fun DrawScope.drawYearLine(chart: ChartUiState, color: Color, selectedColor: Color) {
    val slot = size.width / chart.bars.size
    fun point(i: Int, steps: Int) = Offset((i + 0.5f) * slot, yFor(steps, chart.scaleMax))
    val points = chart.bars.mapIndexedNotNull { i, bar -> bar.steps?.let { point(i, it) } }
    val stroke = 1.5.dp.toPx()
    if (points.size == 1) {
        // A line needs two days; with one, show it as a dot.
        drawCircle(color, radius = stroke, center = points[0])
    } else if (points.isNotEmpty()) {
        val path = Path().apply {
            moveTo(points[0].x, points[0].y)
            points.drop(1).forEach { lineTo(it.x, it.y) }
        }
        drawPath(path, color, style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
    val selectedIndex = chart.bars.indexOfFirst { it.date == chart.selected?.date }
    val selectedSteps = chart.bars.getOrNull(selectedIndex)?.steps
    if (selectedSteps != null) {
        drawCircle(selectedColor, radius = 4.dp.toPx(), center = point(selectedIndex, selectedSteps))
    }
}

private fun DrawScope.drawGoalLine(chart: ChartUiState, color: Color) {
    val y = yFor(chart.goal, chart.scaleMax)
    drawLine(
        color,
        start = Offset(0f, y),
        end = Offset(size.width, y),
        strokeWidth = 1.dp.toPx(),
        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
    )
}

/** Each label centred under its day, kept inside the chart's width. */
@Composable
private fun AxisLabels(chart: ChartUiState, style: TextStyle, modifier: Modifier = Modifier) {
    Layout(
        content = { chart.axisLabels.forEach { Text(text = it.text, style = style, maxLines = 1) } },
        // Read as part of the chart's one description, not letter by letter.
        modifier = modifier.fillMaxWidth().clearAndSetSemantics {},
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(Constraints()) }
        val width = constraints.maxWidth
        val count = chart.bars.size.coerceAtLeast(1)
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            placeables.forEachIndexed { i, placeable ->
                val centre = (chart.axisLabels[i].index + 0.5f) * width / count
                val x = (centre - placeable.width / 2f).roundToInt().coerceIn(0, (width - placeable.width).coerceAtLeast(0))
                placeable.place(x, 0)
            }
        }
    }
}
