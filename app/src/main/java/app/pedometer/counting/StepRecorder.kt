package app.pedometer.counting

import android.util.Log
import app.pedometer.currentLocalDate
import app.pedometer.data.StepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.transform
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.datetime.LocalDate

/**
 * Records every step sensor reading, for the whole app.
 *
 * The foreground service, the catch-up job, the rollover alarm and the visible activity all
 * read through this one object. A single upstream collector does every write, so readings
 * are stored in the order the sensor produced them however many callers are listening.
 * That ordering matters: an older reading stored after a newer one would look like the
 * sensor going backwards and cause steps to be counted twice.
 */
class StepRecorder(
    reader: StepSensorReader,
    private val repository: StepRepository,
    scope: CoroutineScope,
    private val bootCount: () -> Int,
    private val today: () -> LocalDate = ::currentLocalDate,
    private val onRecorded: () -> Unit = {},
) {
    /**
     * Each sensor value, emitted after it has been written. The sensor stays registered for
     * as long as anything collects this flow.
     */
    val recorded: SharedFlow<Long> = reader.readings()
        .transform { value -> if (record(value)) emit(value) }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    /**
     * Waits for the sensor's next reading and records it. Returns false if none arrives in
     * time, which is normal while the phone is lying still.
     */
    suspend fun recordOnce(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean =
        withTimeoutOrNull(timeoutMs) { recorded.first() } != null

    private suspend fun record(value: Long): Boolean = try {
        repository.recordReading(value, bootCount(), today())
        onRecorded()
        true
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        // The baseline was not advanced, so these steps are picked up by the next reading
        // instead of being lost.
        Log.w(TAG, "Could not record a step reading", e)
        false
    }

    private companion object {
        const val TAG = "StepRecorder"
        const val DEFAULT_TIMEOUT_MS = 10_000L
    }
}
