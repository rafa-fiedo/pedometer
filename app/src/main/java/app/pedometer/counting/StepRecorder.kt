package app.pedometer.counting

import android.util.Log
import app.pedometer.data.StepRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onSubscription
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
 *
 * Steps go to the day their last step was taken ([dateOf] its time), not the day the reading
 * arrived: a phone that holds readings back overnight would otherwise put the evening's
 * steps on the next morning.
 */
class StepRecorder(
    reader: StepSensorReader,
    private val repository: StepRepository,
    scope: CoroutineScope,
    private val bootCount: () -> Int,
    private val dateOf: (Long) -> LocalDate = ::localDateAt,
    private val onRecorded: () -> Unit = {},
    /** Asks the sensor to hand over readings it is holding back. */
    private val requestFlush: () -> Unit = reader::flush,
) {
    /**
     * Each sensor value, emitted after it has been written. The sensor stays registered for
     * as long as anything collects this flow.
     */
    val recorded: SharedFlow<Long> = reader.readings()
        .transform { reading -> if (record(reading)) emit(reading.value) }
        .shareIn(scope, SharingStarted.WhileSubscribed(), replay = 0)

    /**
     * Waits for the sensor's next reading and records it. Returns false if none arrives in
     * time, which is normal while the phone is lying still.
     *
     * A newly registered listener gets the current count straight away. One that is already
     * registered (the counting service's) gets nothing until the next step, so the sensor is
     * also flushed: that hands over any steps it is still holding back.
     */
    suspend fun recordOnce(timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean =
        withTimeoutOrNull(timeoutMs) { recorded.onSubscription { requestFlush() }.first() } != null

    private suspend fun record(reading: StepReading): Boolean = try {
        repository.recordReading(reading.value, bootCount(), dateOf(reading.lastStepMillis))
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
