package app.pedometer.counting

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pedometer.core.GoalAlert
import app.pedometer.currentLocalDate
import app.pedometer.data.StepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.LocalDate

/**
 * What has to happen after steps are recorded or a setting changes: refresh the home-screen
 * widget and send the goal-reached notification when it is due.
 */
class StepEffects(
    private val context: Context,
    private val repository: StepRepository,
    scope: CoroutineScope,
    private val today: () -> LocalDate = ::currentLocalDate,
    private val refreshWidget: suspend () -> Unit = {},
) {
    private val requests = Channel<Unit>(Channel.CONFLATED)

    // Serialises runs, so two overlapping refreshes cannot both decide to send the alert.
    private val mutex = Mutex()

    init {
        scope.launch {
            for (request in requests) {
                try {
                    refreshNow()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Could not refresh after recording steps", e)
                }
                // At most one run per interval; the conflated channel keeps the newest
                // request waiting, so the final state is always delivered.
                delay(REFRESH_INTERVAL_MS)
            }
        }
        // The widget shows the goal, and lowering the goal or switching the alert on can
        // make the alert due, so both need a refresh without any new steps.
        scope.launch {
            combine(repository.goal, repository.goalAlertEnabled) { goal, enabled -> goal to enabled }
                .distinctUntilChanged()
                .drop(1)
                .collect { requestRefresh() }
        }
        // The widget reads the date when it draws, so a new day needs a redraw even when no
        // steps arrive; otherwise it would show yesterday's count until the first step.
        val clockFilter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
        }
        val clockReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = requestRefresh()
        }
        ContextCompat.registerReceiver(context, clockReceiver, clockFilter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    /** Asks for a refresh soon. Cheap, safe from any thread, and throttled. */
    fun requestRefresh() {
        requests.trySend(Unit)
    }

    /** Refreshes the widget and sends the goal-reached notification if it is due. */
    suspend fun refreshNow() = mutex.withLock {
        try {
            refreshWidget()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Could not refresh the widget", e)
        }
        sendGoalAlertIfDue()
    }

    private suspend fun sendGoalAlertIfDue() {
        val date = today()
        val goal = repository.goal.first()
        val due = GoalAlert.shouldNotify(
            steps = repository.days.first().filter { it.date == date }.sumOf { it.steps },
            goal = goal,
            today = date,
            lastAlertDate = repository.lastGoalAlertDate.first(),
            enabled = repository.goalAlertEnabled.first(),
        )
        if (!due) return
        // Not marked as sent while notifications are blocked, so the alert can still
        // arrive later the same day once they are allowed again.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        GoalNotification.ensureChannel(context)
        context.getSystemService(NotificationManager::class.java)
            .notify(GoalNotification.NOTIFICATION_ID, GoalNotification.build(context, goal))
        repository.markGoalAlertSent(date)
    }

    private companion object {
        const val TAG = "StepEffects"
        const val REFRESH_INTERVAL_MS = 5_000L
    }
}
