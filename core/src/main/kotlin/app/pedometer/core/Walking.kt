package app.pedometer.core

import kotlinx.datetime.DayOfWeek
import kotlin.math.roundToInt

/** The owner's height and weight, both within the allowed ranges. */
data class BodySize(val heightCm: Int, val weightKg: Int) {
    companion object {
        val HEIGHT_RANGE = 100..250
        val WEIGHT_RANGE = 30..300

        /** Null unless both values are present and allowed. */
        fun of(heightCm: Int?, weightKg: Int?): BodySize? =
            if (heightCm in HEIGHT_RANGE && weightKg in WEIGHT_RANGE) BodySize(heightCm!!, weightKg!!) else null

        /** A whole number typed by the owner, or null when it isn't one or is outside [range]. */
        fun parseMeasure(text: String, range: IntRange): Int? {
            val trimmed = text.trim()
            if (trimmed.isEmpty() || trimmed.any { it !in '0'..'9' }) return null
            return trimmed.toIntOrNull()?.takeIf { it in range }
        }
    }
}

data class WalkEstimate(val km: Double, val kcal: Int)

/** Rough walking distance and calories; rules of thumb, not medical accuracy. */
object Walking {
    private const val STEP_LENGTH_PER_HEIGHT = 0.415
    private const val KCAL_PER_KG_PER_KM = 0.5

    fun estimate(steps: Int, body: BodySize): WalkEstimate {
        // In Double from the start: steps × height in whole numbers overflows on a big year total.
        val km = steps.toDouble() * body.heightCm * STEP_LENGTH_PER_HEIGHT / 100_000
        // Calories are never negative, so rounding to nearest is rounding half up.
        val kcal = (KCAL_PER_KG_PER_KM * body.weightKg * km).roundToInt()
        return WalkEstimate(km, kcal)
    }
}

/** Which day weeks start on: the phone's region default, or a fixed day the owner chose. */
enum class WeekStart {
    PHONE_DEFAULT,
    MONDAY,
    SUNDAY,
    ;

    fun resolve(phoneDefault: DayOfWeek): DayOfWeek = when (this) {
        PHONE_DEFAULT -> phoneDefault
        MONDAY -> DayOfWeek.MONDAY
        SUNDAY -> DayOfWeek.SUNDAY
    }
}
