package app.pedometer.ui

import android.Manifest
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.work.testing.WorkManagerTestInitHelper
import app.pedometer.PedometerApp
import app.pedometer.currentLocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.test.assertEquals

/**
 * Drives the real activity on the JVM. The test environment has no step sensor, which is
 * exactly the case the no-sensor screen and the debug `skip_sensor_check` extra exist for.
 */
@RunWith(RobolectricTestRunner::class)
// A typical phone screen; the test default (320x470dp) is smaller than any supported phone.
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class AppUiTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app: PedometerApp get() = ApplicationProvider.getApplicationContext()

    // On a device WorkManager initialises itself at app start; a JVM test has to do it.
    @Before
    fun initWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(app)
    }

    private fun launch(skipSensorCheck: Boolean): ActivityScenario<MainActivity> =
        ActivityScenario.launch(
            Intent(app, MainActivity::class.java).putExtra("skip_sensor_check", skipSensorCheck),
        )

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun phoneWithoutStepSensorSeesTheNoSensorMessage() {
        launch(skipSensorCheck = false).use {
            compose.onNodeWithText("This phone can't count steps").assertIsDisplayed()
        }
    }

    @Test
    fun withoutActivityAccessThePermissionScreenIsShown() {
        launch(skipSensorCheck = true).use {
            compose.onNodeWithText("Count your steps").assertIsDisplayed()
            compose.onNodeWithText("Allow access").assertIsDisplayed()
        }
    }

    @Test
    fun accessGrantedInSystemSettingsStillLeadsToTheNotificationRequest() {
        launch(skipSensorCheck = true).use { scenario ->
            compose.onNodeWithText("Count your steps").assertIsDisplayed()

            // The user grants access in the system settings and comes back to the app.
            shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
            scenario.moveToState(Lifecycle.State.STARTED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            compose.waitForIdle()

            scenario.onActivity { activity ->
                val request = shadowOf(activity).lastRequestedPermission
                assertEquals(
                    listOf(Manifest.permission.POST_NOTIFICATIONS),
                    request?.requestedPermissions?.toList(),
                )
            }
        }
    }

    @Test
    fun settingsShowsTheRowsAndSavesTheStepCountSwitch() {
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)

        launch(skipSensorCheck = true).use {
            waitForText("Keep counting in the background")
            compose.onNodeWithText("Not now").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Keep counting in the background").fetchSemanticsNodes().isEmpty()
            }

            // Settings is the fourth tab; the Today screen no longer has a gear for it.
            waitForText("of 10,000 steps")
            assertEquals(0, compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().size)
            compose.onNodeWithText("Settings").performClick()
            waitForText("Daily goal")
            compose.onNodeWithText("10,000 steps").assertIsDisplayed()
            compose.onNodeWithText("Tell me when I reach my goal").assertIsDisplayed()
            // The tabs stay visible on the Settings screen, and it has no back arrow.
            compose.onNodeWithText("Today").assertIsDisplayed()
            assertEquals(0, compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size)

            compose.onNodeWithText("Show step count").performClick()
            waitForText(
                "With the step count off, steps are collected about every 15 minutes " +
                    "and whenever you open the app.",
            )
            assertEquals(false, runBlocking { app.repository.showStepCount.first() })
        }
    }

    @Test
    fun recordedStepsAppearOnAllThreeScreens() {
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val today = currentLocalDate()
        runBlocking {
            app.repository.addSteps(today, 12_870)
            app.repository.addSteps(today.minus(3, DateTimeUnit.DAY), 6_482)
        }

        launch(skipSensorCheck = true).use {
            // One-time battery prompt, then the Today screen.
            waitForText("Keep counting in the background")
            compose.onNodeWithText("Not now").performClick()
            waitForText("12,870")
            compose.onNodeWithText("of 10,000 steps").assertIsDisplayed()
            compose.onNodeWithText("This week").assertIsDisplayed()
            compose.onNodeWithText("This month").assertIsDisplayed()
            // The prompt closes once its "shown" flag has been saved.
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText("Keep counting in the background").fetchSemanticsNodes().isEmpty()
            }

            compose.onNodeWithContentDescription("Change goal").assertIsDisplayed()

            // History: four days, newest first, with the two gap days shown as 0.
            compose.onNodeWithText("History").performClick()
            waitForText("6,482")
            compose.onNodeWithText("12,870").assertIsDisplayed()
            assertEquals(2, compose.onAllNodesWithText("0").fetchSemanticsNodes().size)
            assertEquals(1, compose.onAllNodesWithContentDescription("Goal reached").fetchSemanticsNodes().size)

            // Lowering the goal re-marks past days and updates the Today screen. The goal is
            // changed through the repository because the goal dialog's text field never lets
            // the JVM test clock go idle; the dialog itself is checked by hand on a phone.
            runBlocking { app.repository.setGoal(5_000) }
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithContentDescription("Goal reached").fetchSemanticsNodes().size == 2
            }
            compose.onNodeWithText("Today").performClick()
            waitForText("of 5,000 steps")

            // Scores: today's total and both records show today's count.
            compose.onNodeWithText("Scores").performClick()
            waitForText("Best day this month")
            compose.onNodeWithText("Best day this year").assertIsDisplayed()
            assertEquals(true, compose.onAllNodesWithText("12,870").fetchSemanticsNodes().size >= 3)
        }
    }
}
