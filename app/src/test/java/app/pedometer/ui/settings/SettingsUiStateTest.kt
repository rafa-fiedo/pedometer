package app.pedometer.ui.settings

import app.pedometer.core.WeekStart
import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsUiStateTest {
    private fun state(
        goal: Int = 10_000,
        showStepCount: Boolean = true,
        goalAlertEnabled: Boolean = false,
        heightCm: Int? = null,
        weightKg: Int? = null,
        weekStart: WeekStart = WeekStart.PHONE_DEFAULT,
        locale: Locale = Locale.US,
    ) = settingsUiState(goal, showStepCount, goalAlertEnabled, heightCm, weightKg, weekStart, locale)

    @Test
    fun mapsValues() {
        val s = state()
        assertEquals("10,000 steps", s.goalText)
        assertEquals(10_000, s.goal)
        assertTrue(s.showStepCount)
        assertEquals(false, s.goalAlertEnabled)
        assertEquals(false, s.showPeriodicNote)
    }

    @Test
    fun noteAppearsWhenTheStepCountIsOff() =
        assertTrue(state(goal = 8_000, showStepCount = false, goalAlertEnabled = true).showPeriodicNote)

    @Test
    fun bodyValuesPassThrough() {
        val s = state(heightCm = 178, weightKg = null, weekStart = WeekStart.SUNDAY)
        assertEquals(178, s.heightCm)
        assertNull(s.weightKg)
        assertEquals(WeekStart.SUNDAY, s.weekStart)
    }

    @Test
    fun phoneDefaultDayIsNamed() {
        assertEquals("Sunday", state(locale = Locale.US).phoneDefaultDay)
        assertEquals("Monday", state(locale = Locale.UK).phoneDefaultDay)
    }
}
