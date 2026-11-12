package app.pedometer.ui.onboarding

enum class StartupScreen { NO_SENSOR, PERMISSION, MAIN }

/**
 * Decides what the app shows on launch. [skipSensorCheck] exists so debug builds can show the
 * main screens on an emulator, which has no step sensor.
 */
fun startupScreen(
    hasSensor: Boolean,
    hasActivityPermission: Boolean,
    skipSensorCheck: Boolean,
): StartupScreen = when {
    !hasSensor && !skipSensorCheck -> StartupScreen.NO_SENSOR
    !hasActivityPermission -> StartupScreen.PERMISSION
    else -> StartupScreen.MAIN
}
