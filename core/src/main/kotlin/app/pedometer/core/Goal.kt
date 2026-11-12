package app.pedometer.core

/** Rules for the daily step goal. */
object Goal {
    const val MIN = 500
    const val MAX = 100_000
    const val DEFAULT = 10_000

    fun isValid(value: Int): Boolean = value in MIN..MAX

    /** Parses user input; returns null unless it is plain digits within the goal range. */
    fun parse(text: String): Int? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it !in '0'..'9' }) return null
        return trimmed.toIntOrNull()?.takeIf(::isValid)
    }
}
