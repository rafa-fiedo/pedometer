package app.pedometer.counting

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.pedometer.data.AppDatabase
import app.pedometer.data.SettingsStore
import app.pedometer.data.StepRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepEffectsTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val notificationManager = context.getSystemService(NotificationManager::class.java)
    private val effectsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val widgetRefreshes = AtomicInteger()
    private lateinit var db: AppDatabase
    private lateinit var repo: StepRepository
    private lateinit var effects: StepEffects

    private fun d(iso: String) = LocalDate.parse(iso)

    private fun notifications(): List<Notification> = shadowOf(notificationManager).allNotifications

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val dataStore = PreferenceDataStoreFactory.create(
            produceFile = { File(tmp.root, "settings.preferences_pb") },
        )
        repo = StepRepository(db.stepDao(), SettingsStore(dataStore))
        effects = StepEffects(
            context = context,
            repository = repo,
            scope = effectsScope,
            today = { d("2026-11-12") },
            refreshWidget = { widgetRefreshes.incrementAndGet() },
        )
    }

    @After
    fun tearDown() {
        effectsScope.cancel()
        db.close()
    }

    @Test
    fun sendsOnceWhenTheGoalIsReached() = runBlocking {
        repo.setGoalAlertEnabled(true)
        repo.addSteps(d("2026-11-12"), 10_000)

        effects.refreshNow()
        effects.refreshNow()

        assertEquals(1, notifications().size)
        val extras = notifications().single().extras
        assertEquals("Goal reached", extras.getString(Notification.EXTRA_TITLE))
        assertEquals("You walked 10,000 steps today.", extras.getString(Notification.EXTRA_TEXT))
        assertEquals(d("2026-11-12"), repo.lastGoalAlertDate.first())
    }

    @Test
    fun staysQuietBelowTheGoalOrWhenSwitchedOff() = runBlocking {
        repo.addSteps(d("2026-11-12"), 12_000)
        effects.refreshNow() // the switch is off

        repo.setGoal(20_000)
        repo.setGoalAlertEnabled(true)
        effects.refreshNow() // on, but below the goal

        assertTrue(notifications().isEmpty())
        assertNull(repo.lastGoalAlertDate.first())
    }

    @Test
    fun blockedNotificationsAreNotMarkedAsSent() = runBlocking {
        shadowOf(notificationManager).setNotificationsEnabled(false)
        repo.setGoalAlertEnabled(true)
        repo.addSteps(d("2026-11-12"), 10_000)

        effects.refreshNow()
        assertNull(repo.lastGoalAlertDate.first())

        shadowOf(notificationManager).setNotificationsEnabled(true)
        effects.refreshNow()
        assertEquals(1, notifications().size)
    }

    @Test
    fun refreshesTheWidgetEveryTime() = runBlocking {
        effects.refreshNow()
        effects.refreshNow()
        assertEquals(2, widgetRefreshes.get())
    }
}
