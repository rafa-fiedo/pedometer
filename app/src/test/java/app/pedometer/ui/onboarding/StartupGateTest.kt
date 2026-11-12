package app.pedometer.ui.onboarding

import org.junit.Test
import kotlin.test.assertEquals

class StartupGateTest {
    @Test
    fun noSensorWins() = assertEquals(StartupScreen.NO_SENSOR, startupScreen(false, true, false))

    @Test
    fun missingPermission() = assertEquals(StartupScreen.PERMISSION, startupScreen(true, false, false))

    @Test
    fun ready() = assertEquals(StartupScreen.MAIN, startupScreen(true, true, false))

    @Test
    fun skipFlagBypassesOnlyTheSensorCheck() {
        assertEquals(StartupScreen.PERMISSION, startupScreen(false, false, true))
        assertEquals(StartupScreen.MAIN, startupScreen(false, true, true))
    }
}
