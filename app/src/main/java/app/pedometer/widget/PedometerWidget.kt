package app.pedometer.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.DpSize
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
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.semantics.semantics
import androidx.glance.semantics.testTag
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.pedometer.PedometerApp
import app.pedometer.R
import app.pedometer.currentLocalDate
import app.pedometer.ui.appLaunchIntent
import app.pedometer.ui.firstDayOfWeek
import kotlinx.coroutines.flow.first
import java.util.Locale

/** Marks the progress bar, so tests can tell the three-cell layout from the two-cell one. */
const val PROGRESS_TAG = "progress"

/** The home-screen widget: one row tall, 1 to 4 cells wide, showing more as it widens. */
class PedometerWidget : GlanceAppWidget() {
    // One layout per width; Android picks the largest that fits the widget's actual size.
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(
            DpSize(57.dp, 40.dp),
            DpSize(130.dp, 40.dp),
            DpSize(203.dp, 40.dp),
            DpSize(276.dp, 40.dp),
        ),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = (context.applicationContext as PedometerApp).repository
        // Read up front, so the first frame already shows real numbers.
        val initialDays = repository.days.first()
        val initialGoal = repository.goal.first()
        provideContent {
            // Collected, so new steps and goal changes redraw the widget while it is updating.
            val days by repository.days.collectAsState(initialDays)
            val goal by repository.goal.collectAsState(initialGoal)
            val locale = Locale.getDefault()
            val state = widgetState(days, goal, currentLocalDate(), firstDayOfWeek(locale), locale)
            GlanceTheme {
                WidgetContent(state, widgetWidth(LocalSize.current.width.value))
            }
        }
    }
}

class PedometerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PedometerWidget()
}

@Composable
fun WidgetContent(state: WidgetState, width: WidgetWidth) {
    val context = LocalContext.current
    val container = GlanceModifier
        .fillMaxSize()
        .appWidgetBackground()
        .background(GlanceTheme.colors.widgetBackground)
        .cornerRadius(16.dp)
        .clickable(actionStartActivity(appLaunchIntent(context)))

    if (width == WidgetWidth.ONE) {
        Column(
            modifier = container,
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WalkIcon(size = 16)
            Text(text = state.stepsText, style = valueStyle(13), maxLines = 1)
        }
        return
    }

    Row(
        modifier = container.padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WalkIcon(size = 22)
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(text = state.stepsText, style = valueStyle(18), maxLines = 1)
            Text(text = state.goalText, style = labelStyle(), maxLines = 1)
        }
        if (width == WidgetWidth.THREE || width == WidgetWidth.FOUR) {
            Spacer(GlanceModifier.width(12.dp))
            LinearProgressIndicator(
                progress = state.progress,
                modifier = GlanceModifier.defaultWeight().height(6.dp).semantics { testTag = PROGRESS_TAG },
                color = GlanceTheme.colors.primary,
                backgroundColor = GlanceTheme.colors.surfaceVariant,
            )
        }
        if (width == WidgetWidth.FOUR) {
            Spacer(GlanceModifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(text = state.weekText, style = valueStyle(14), maxLines = 1)
                Text(text = context.getString(R.string.widget_this_week), style = labelStyle(), maxLines = 1)
            }
        }
    }
}

@Composable
private fun WalkIcon(size: Int) {
    Image(
        provider = ImageProvider(R.drawable.ic_walk),
        contentDescription = null,
        modifier = GlanceModifier.size(size.dp),
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
    )
}

@Composable
private fun valueStyle(fontSize: Int) = TextStyle(
    color = GlanceTheme.colors.onSurface,
    fontSize = fontSize.sp,
    fontWeight = FontWeight.Medium,
)

@Composable
private fun labelStyle() = TextStyle(
    color = GlanceTheme.colors.onSurfaceVariant,
    fontSize = 12.sp,
)
