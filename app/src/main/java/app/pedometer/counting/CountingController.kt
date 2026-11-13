package app.pedometer.counting

import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.pedometer.data.StepRepository
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

enum class CountingMode {
    /** The phone has no step sensor, or the app may not read it yet. */
    NONE,

    /** Foreground service with the permanent notification; every step is recorded at once. */
    CONTINUOUS,

    /** No notification; steps are collected by a background job about every 15 minutes. */
    PERIODIC,
}

fun countingMode(canCount: Boolean, showStepCount: Boolean): CountingMode = when {
    !canCount -> CountingMode.NONE
    showStepCount -> CountingMode.CONTINUOUS
    else -> CountingMode.PERIODIC
}

/**
 * Puts the app into the counting mode the "Show step count" setting asks for. Safe to call
 * repeatedly; it is called when the app opens, after a reboot and when the setting changes.
 */
class CountingController(
    private val context: Context,
    private val repository: StepRepository,
) {
    suspend fun apply() {
        val mode = countingMode(
            canCount = StepCounterService.canCount(context),
            showStepCount = repository.showStepCount.first(),
        )
        val workManager = WorkManager.getInstance(context)
        when (mode) {
            CountingMode.NONE -> {
                stopService()
                cancelCatchUp(workManager)
            }
            CountingMode.CONTINUOUS -> {
                cancelCatchUp(workManager)
                StepCounterService.start(context)
            }
            CountingMode.PERIODIC -> {
                stopService()
                workManager.enqueueUniquePeriodicWork(
                    CATCH_UP_WORK,
                    // KEEP: re-applying must not restart the 15-minute interval each time.
                    ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<StepCatchUpWorker>(CATCH_UP_MINUTES, TimeUnit.MINUTES).build(),
                )
                // One run straight away: this is the reading after a reboot and right after
                // the step count is switched off.
                workManager.enqueueUniqueWork(
                    CATCH_UP_NOW_WORK,
                    ExistingWorkPolicy.REPLACE,
                    OneTimeWorkRequestBuilder<StepCatchUpWorker>().build(),
                )
                // In continuous mode the service schedules this alarm itself.
                scheduleRolloverAlarm(context)
            }
        }
    }

    private fun stopService() {
        context.stopService(Intent(context, StepCounterService::class.java))
    }

    private fun cancelCatchUp(workManager: WorkManager) {
        workManager.cancelUniqueWork(CATCH_UP_WORK)
        workManager.cancelUniqueWork(CATCH_UP_NOW_WORK)
    }

    private companion object {
        const val CATCH_UP_WORK = "step-catch-up"
        const val CATCH_UP_NOW_WORK = "step-catch-up-now"

        // The shortest interval Android allows for periodic background work.
        const val CATCH_UP_MINUTES = 15L
    }
}
