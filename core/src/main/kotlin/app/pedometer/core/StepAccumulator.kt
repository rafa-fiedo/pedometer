package app.pedometer.core

/** The last reading seen from the step sensor, and the boot it belonged to. */
data class AccumulatorState(val lastSensorValue: Long, val lastBootCount: Int)

data class AccumulatorResult(val stepsToAdd: Int, val state: AccumulatorState)

/**
 * Turns readings of a step sensor that counts cumulatively since boot into steps to add.
 */
object StepAccumulator {
    fun apply(previous: AccumulatorState?, sensorValue: Long, bootCount: Int): AccumulatorResult {
        val value = sensorValue.coerceAtLeast(0)
        val steps: Long = when {
            // First reading ever: nothing to compare against, so it becomes the baseline.
            previous == null -> 0
            // The phone rebooted, so the sensor restarted from zero and every step is new.
            previous.lastBootCount != bootCount -> value
            value > previous.lastSensorValue -> value - previous.lastSensorValue
            // Same boot but the value did not rise: add nothing and re-baseline.
            else -> 0
        }
        return AccumulatorResult(
            stepsToAdd = steps.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
            state = AccumulatorState(value, bootCount),
        )
    }
}
