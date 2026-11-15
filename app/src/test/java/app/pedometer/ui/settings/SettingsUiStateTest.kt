package app.pedometer.ui.settings

import org.junit.Test
import java.util.Locale
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SettingsUiStateTest {
    @Test
    fun mapsValues() = assertEquals(
        SettingsUiState(
            goalText = "10,000 steps",
            goal = 10_000,
            showStepCount = true,
            goalAlertEnabled = false,
            showPeriodicNote = false,
        ),
        settingsUiState(10_000, showStepCount = true, goalAlertEnabled = false, Locale.US),
    )

    @Test
    fun noteAppearsWhenTheStepCountIsOff() =
        assertTrue(settingsUiState(8_000, showStepCount = false, goalAlertEnabled = true, Locale.US).showPeriodicNote)
}
