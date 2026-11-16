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
import app.pedometer.core.ChartRange
import app.pedometer.core.StepChart
import app.pedometer.core.WeekStart
import app.pedometer.currentLocalDate
import app.pedometer.ui.scores.formatPeriodLabel
import kotlinx.datetime.DayOfWeek
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
import java.util.Locale
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
    fun chosenWeekStartShapesTheScoresWeek() {
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        // The test locale (en-US) starts weeks on Sunday, so Monday differs from the default.
        runBlocking { app.repository.setWeekStart(WeekStart.MONDAY) }
        val today = currentLocalDate()

        launch(skipSensorCheck = true).use {
            waitForText("Keep counting in the background")
            compose.onNodeWithText("Not now").performClick()
            compose.onNodeWithText("Scores").performClick()
            waitForText(
                formatPeriodLabel(
                    StepChart.periodContaining(ChartRange.WEEK, today, DayOfWeek.MONDAY),
                    Locale.getDefault(),
                ),
            )
        }
    }

    @Test
    fun theCaloriesLinkOpensSettings() {
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)

        launch(skipSensorCheck = true).use {
            waitForText("Keep counting in the background")
            compose.onNodeWithText("Not now").performClick()
            waitForText("Add your height and weight to see calories")
            compose.onNodeWithText("Add your height and weight to see calories").performClick()
            waitForText("Daily goal")
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
            waitForText("of 6,000 steps")
            assertEquals(0, compose.onAllNodesWithContentDescription("Settings").fetchSemanticsNodes().size)
            compose.onNodeWithText("Settings").performClick()
            waitForText("Daily goal")
            compose.onNodeWithText("6,000 steps").assertIsDisplayed()
            compose.onNodeWithText("Tell me when I reach my goal").assertIsDisplayed()
            // The tabs stay visible on the Settings screen, and it has no back arrow.
            compose.onNodeWithText("Today").assertIsDisplayed()
            assertEquals(0, compose.onAllNodesWithContentDescription("Back").fetchSemanticsNodes().size)

            // A fresh install starts with the step count off, so the catch-up note is shown;
            // switching it on hides the note and saves the choice.
            val note = "With the step count off, steps are collected about every 15 minutes " +
                "and whenever you open the app."
            waitForText(note)
            compose.onNodeWithText("Show step count").performClick()
            compose.waitUntil(timeoutMillis = 10_000) {
                compose.onAllNodesWithText(note).fetchSemanticsNodes().isEmpty()
            }
            assertEquals(true, runBlocking { app.repository.showStepCount.first() })
        }
    }

    @Test
    fun recordedStepsAppearOnAllThreeScreens() {
        shadowOf(app).grantPermissions(Manifest.permission.ACTIVITY_RECOGNITION)
        val today = currentLocalDate()
        runBlocking {
            // A 10,000 goal, so only the 12,870 day counts as reached until the goal is lowered.
            app.repository.setGoal(10_000)
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

            // Calories need height and weight; until then Today offers a way to add them.
            compose.onNodeWithText("Add your height and weight to see calories").assertIsDisplayed()
            runBlocking {
                app.repository.setHeightCm(178)
                app.repository.setWeightKg(72)
            }
            waitForText("342 kcal · 9.5 km")

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

            // Scores: both records show today's count, and the chart switches to the month.
            compose.onNodeWithText("Scores").performClick()
            waitForText("Best day this month")
            compose.onNodeWithText("Best day this year").assertIsDisplayed()
            assertEquals(true, compose.onAllNodesWithText("12,870").fetchSemanticsNodes().size >= 2)
            compose.onNodeWithText("Month").performClick()
            waitForText(formatMonthTitle(today, Locale.getDefault()))
        }
    }
}
