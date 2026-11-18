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
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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

/** Marks the refresh arrow, on every layout and on the round widget. */
const val REFRESH_TAG = "refresh"

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
        val initialWeekStart = repository.weekStart.first()
        provideContent {
            // Collected, so new steps and goal changes redraw the widget while it is updating.
            val days by repository.days.collectAsState(initialDays)
            val goal by repository.goal.collectAsState(initialGoal)
            val weekStart by repository.weekStart.collectAsState(initialWeekStart)
            val locale = Locale.getDefault()
            val state = widgetState(days, goal, currentLocalDate(), weekStart.resolve(firstDayOfWeek(locale)), locale)
            GlanceTheme {
                WidgetContent(state, widgetWidth(LocalSize.current.width.value))
            }
        }
    }
}

/**
 * One receiver per entry in the widget picker, each starting at its own width (see the
 * matching `xml/pedometer_widget_*` file). All draw the same widget and can be resized. The
 * original receiver is the 2 x 1 entry, so widgets already on home screens keep working.
 */
class PedometerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PedometerWidget()
}

class PedometerWidget1x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PedometerWidget()
}

class PedometerWidget3x1Receiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = PedometerWidget()
}

class PedometerWidget4x1Receiver : GlanceAppWidgetReceiver() {
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
        Box(modifier = container) {
            Column(
                modifier = GlanceModifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WalkIcon(size = 16)
                Text(text = state.stepsText, style = valueStyle(13), maxLines = 1)
            }
            // A full-size layer aligned top-end puts the small arrow in the corner.
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                RefreshArrow(iconSize = 14, modifier = GlanceModifier.padding(4.dp))
            }
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
        // At two cells nothing stretches, so a flexible gap pushes the arrow to the right end.
        Spacer(if (width == WidgetWidth.TWO) GlanceModifier.defaultWeight() else GlanceModifier.width(8.dp))
        RefreshArrow(iconSize = 20)
    }
}

/** Reads the step sensor now instead of waiting for the next background catch-up. */
@Composable
fun RefreshArrow(iconSize: Int, modifier: GlanceModifier = GlanceModifier) {
    Image(
        provider = ImageProvider(R.drawable.ic_refresh),
        contentDescription = LocalContext.current.getString(R.string.widget_refresh),
        modifier = modifier
            .size(iconSize.dp)
            .clickable(actionRunCallback<RefreshWidgetAction>())
            .semantics { testTag = REFRESH_TAG },
        colorFilter = ColorFilter.tint(GlanceTheme.colors.primary),
    )
}

@Composable
private fun WalkIcon(size: Int) {
    Image(
        provider = ImageProvider(R.drawable.ic_footprints),
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
