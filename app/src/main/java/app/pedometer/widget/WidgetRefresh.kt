package app.pedometer.widget

import android.content.Context
import android.util.Log
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import app.pedometer.PedometerApp
import app.pedometer.counting.StepCounterService
import kotlinx.coroutines.CancellationException

private const val TAG = "WidgetRefresh"

/** How long a tap on a widget waits for the step sensor before redrawing anyway. */
private const val REFRESH_READING_TIMEOUT_MS = 5_000L

/**
 * Reads the step sensor once (when the app may), then always redraws the widgets, so a tap
 * shows something even when the phone gives no new reading.
 */
suspend fun refreshSteps(canCount: Boolean, recordOnce: suspend () -> Boolean, redraw: suspend () -> Unit) {
    if (canCount) {
        try {
            recordOnce()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not read the step sensor", e)
        }
    }
    redraw()
}

/** Runs when the refresh arrow, or the round widget, is tapped. */
class RefreshWidgetAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val app = context.applicationContext as PedometerApp
        refreshSteps(
            canCount = StepCounterService.canCount(app),
            recordOnce = { app.recorder.recordOnce(REFRESH_READING_TIMEOUT_MS) },
            redraw = { app.effects.refreshNow() },
        )
    }
}
