package app.pedometer.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.size
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.pedometer.PedometerApp
import app.pedometer.R
import app.pedometer.currentLocalDate
import app.pedometer.ui.firstDayOfWeek
import kotlinx.coroutines.flow.first
import java.util.Locale
import kotlin.math.roundToInt

/** The circle's diameter: about the size of an app icon. */
private val ROUND_DIAMETER = 56.dp

/** Six-digit counts ("12,870") get a smaller font so they fit inside the circle. */
fun roundStepsFontSp(stepsText: String): Int = if (stepsText.length > 5) 10 else 12

/** Redraws every Pedometer Pal widget, rectangular and round. */
suspend fun updateAllWidgets(context: Context) {
    PedometerWidget().updateAll(context)
    RoundStepsWidget().updateAll(context)
}

/**
 * A 1 × 1 widget that sits on the home screen like an app icon: a circle with the footprints
 * and today's steps, and a ring filling towards the goal. Tapping it refreshes the count.
 */
class RoundStepsWidget : GlanceAppWidget() {
    // Exact, so the circle can shrink to fit a cell smaller than an icon.
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as PedometerApp).repository
        val initialDays = repository.days.first()
        val initialGoal = repository.goal.first()
        val initialWeekStart = repository.weekStart.first()
        provideContent {
            val days by repository.days.collectAsState(initialDays)
            val goal by repository.goal.collectAsState(initialGoal)
            val weekStart by repository.weekStart.collectAsState(initialWeekStart)
            val locale = Locale.getDefault()
            val state = widgetState(days, goal, currentLocalDate(), weekStart.resolve(firstDayOfWeek(locale)), locale)
            GlanceTheme {
                val glanceContext = LocalContext.current
                val colors = GlanceTheme.colors
                val circle = colors.widgetBackground.getColor(glanceContext).toArgb()
                val track = colors.surfaceVariant.getColor(glanceContext).toArgb()
                val fill = colors.primary.getColor(glanceContext).toArgb()
                val sizePx = (ROUND_DIAMETER.value * glanceContext.resources.displayMetrics.density).roundToInt()
                val ring = remember(state.progress, circle, track, fill, sizePx) {
                    drawGoalRing(sizePx, state.progress, circle, track, fill)
                }
                RoundWidgetContent(state, ring)
            }
        }
    }
}

class RoundStepsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = RoundStepsWidget()
}

@Composable
fun RoundWidgetContent(state: WidgetState, ring: Bitmap) {
    val context = LocalContext.current
    val cell = LocalSize.current
    val diameter = minOf(ROUND_DIAMETER, cell.width, cell.height)
    // No background: outside the circle the home screen shows through.
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionRunCallback<RefreshWidgetAction>())
            .semantics { contentDescription = context.getString(R.string.widget_round_description, state.stepsText) },
        contentAlignment = Alignment.Center,
    ) {
        Box(modifier = GlanceModifier.size(diameter), contentAlignment = Alignment.Center) {
            Image(provider = ImageProvider(ring), contentDescription = null, modifier = GlanceModifier.fillMaxSize())
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    provider = ImageProvider(R.drawable.ic_footprints),
                    contentDescription = null,
                    modifier = GlanceModifier.size(18.dp),
                    colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
                )
                Text(
                    text = state.stepsText,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = roundStepsFontSp(state.stepsText).sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
