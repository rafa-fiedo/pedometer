package app.pedometer.data

import android.content.Context
import app.pedometer.testing.InMemoryPreferencesDataStore
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pedometer.core.BodySize
import app.pedometer.core.WeekStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepRepositoryTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: StepDao
    private lateinit var repo: StepRepository
    private lateinit var dataStore: DataStore<Preferences>

    private fun d(iso: String) = LocalDate.parse(iso)

    private suspend fun steps(date: String): Int =
        repo.days.first().firstOrNull { it.date == d(date) }?.steps ?: 0

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.stepDao()
        dataStore = InMemoryPreferencesDataStore()
        repo = StepRepository(dao, SettingsStore(dataStore))
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun firstReadingAddsNothingThenDifferencesAccumulate() = runTest {
        repo.recordReading(500, 3, d("2026-11-10"))
        assertEquals(0, steps("2026-11-10"))
        repo.recordReading(525, 3, d("2026-11-10"))
        assertEquals(25, steps("2026-11-10"))
        repo.recordReading(530, 3, d("2026-11-10"))
        assertEquals(30, steps("2026-11-10"))
    }

    @Test
    fun rebootAddsStepsSinceBoot() = runTest {
        repo.recordReading(100, 1, d("2026-11-10"))
        repo.recordReading(130, 1, d("2026-11-10"))
        repo.recordReading(12, 2, d("2026-11-10"))
        assertEquals(42, steps("2026-11-10"))
    }

    @Test
    fun stepsGoToTheDatePassedIn() = runTest {
        repo.recordReading(100, 1, d("2026-11-10"))
        repo.recordReading(130, 1, d("2026-11-10"))
        repo.recordReading(150, 1, d("2026-11-11"))
        assertEquals(30, steps("2026-11-10"))
        assertEquals(20, steps("2026-11-11"))
    }

    @Test
    fun baselineIsSavedWithTheSteps() = runTest {
        repo.recordReading(100, 1, d("2026-11-10"))
        repo.recordReading(130, 1, d("2026-11-10"))
        assertEquals(
            CounterStateEntity(id = 0, lastSensorValue = 130, lastBootCount = 1),
            dao.counterState(),
        )
    }

    @Test
    fun failedBaselineWriteRollsBackTheSteps() = runTest {
        repo.recordReading(100, 1, d("2026-11-10"))
        repo.recordReading(130, 1, d("2026-11-10"))
        db.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER fail_state BEFORE INSERT ON counter_state " +
                "BEGIN SELECT RAISE(ABORT, 'baseline write failed'); END",
        )

        assertFails { repo.recordReading(150, 1, d("2026-11-10")) }

        // Had the 20 steps stayed while the baseline did not move, the next reading
        // would count them a second time.
        assertEquals(30, steps("2026-11-10"))
    }

    @Test
    fun addStepsSums() = runTest {
        repo.addSteps(d("2026-11-07"), 400)
        repo.addSteps(d("2026-11-07"), 100)
        assertEquals(500, steps("2026-11-07"))
    }

    @Test
    fun goalDefaultsAndPersists() = runTest {
        assertEquals(10_000, repo.goal.first())
        repo.setGoal(8_000)
        assertEquals(8_000, repo.goal.first())
        assertFailsWith<IllegalArgumentException> { repo.setGoal(100) }
    }

    @Test
    fun notificationSwitchesDefaultAndPersist() = runTest {
        assertTrue(repo.showStepCount.first())
        assertFalse(repo.goalAlertEnabled.first())
        repo.setShowStepCount(false)
        repo.setGoalAlertEnabled(true)
        assertFalse(repo.showStepCount.first())
        assertTrue(repo.goalAlertEnabled.first())
    }

    @Test
    fun goalAlertDateDefaultsToNullAndPersists() = runTest {
        assertNull(repo.lastGoalAlertDate.first())
        repo.markGoalAlertSent(d("2026-11-12"))
        assertEquals(d("2026-11-12"), repo.lastGoalAlertDate.first())
    }

    @Test
    fun unreadableGoalAlertDateCountsAsNeverSent() = runTest {
        dataStore.edit { it[stringPreferencesKey("last_goal_alert_date")] = "not-a-date" }
        assertNull(repo.lastGoalAlertDate.first())
    }

    @Test
    fun batteryPromptFlag() = runTest {
        assertFalse(repo.batteryPromptShown.first())
        repo.markBatteryPromptShown()
        assertTrue(repo.batteryPromptShown.first())
    }

    @Test
    fun heightAndWeightStartUnsetAndRoundTrip() = runTest {
        assertNull(repo.heightCm.first())
        assertNull(repo.weightKg.first())
        assertNull(repo.bodySize.first())
        repo.setHeightCm(178)
        repo.setWeightKg(72)
        assertEquals(178, repo.heightCm.first())
        assertEquals(72, repo.weightKg.first())
        assertEquals(BodySize(178, 72), repo.bodySize.first())
        repo.setWeightKg(null)
        assertNull(repo.weightKg.first())
        assertNull(repo.bodySize.first())
    }

    @Test
    fun outOfRangeStoredValuesCountAsUnset() = runTest {
        dataStore.edit { it[intPreferencesKey("height_cm")] = 999 }
        assertNull(repo.heightCm.first())
        assertFailsWith<IllegalArgumentException> { repo.setHeightCm(999) }
    }

    @Test
    fun weekStartDefaultsToPhoneAndRoundTrips() = runTest {
        assertEquals(WeekStart.PHONE_DEFAULT, repo.weekStart.first())
        repo.setWeekStart(WeekStart.SUNDAY)
        assertEquals(WeekStart.SUNDAY, repo.weekStart.first())
    }

    @Test
    fun unknownWeekStartFallsBackToPhoneDefault() = runTest {
        dataStore.edit { it[stringPreferencesKey("week_start")] = "TUESDAY" }
        assertEquals(WeekStart.PHONE_DEFAULT, repo.weekStart.first())
    }
}
