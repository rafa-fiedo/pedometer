package app.pedometer.data

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepRepositoryTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var dao: StepDao
    private lateinit var repo: StepRepository

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
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
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
    fun batteryPromptFlag() = runTest {
        assertFalse(repo.batteryPromptShown.first())
        repo.markBatteryPromptShown()
        assertTrue(repo.batteryPromptShown.first())
    }
}
