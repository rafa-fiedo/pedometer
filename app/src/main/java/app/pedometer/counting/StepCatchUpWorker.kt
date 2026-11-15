package app.pedometer.counting

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.pedometer.PedometerApp
import kotlinx.coroutines.CancellationException

/**
 * The background job of periodic counting: takes one reading from the step sensor, records
 * it, then refreshes the widget and the goal alert.
 */
class StepCatchUpWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as PedometerApp
        if (!StepCounterService.canCount(app)) return Result.success()
        try {
            // No reading within the timeout is normal while the phone lies still.
            app.recorder.recordOnce()
            // Runs even without a new reading, so the widget rolls over to a new day.
            app.effects.refreshNow()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Catch-up run failed", e)
        }
        // Always success: the periodic schedule brings the next run, and nothing is lost
        // meanwhile because the sensor's count is cumulative.
        return Result.success()
    }

    private companion object {
        const val TAG = "StepCatchUpWorker"
    }
}
