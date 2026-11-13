package app.pedometer.counting

import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import app.pedometer.PedometerApp
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepCatchUpWorkerTest {
    /** The test environment has no step sensor, like a phone that cannot count. */
    @Test
    fun succeedsAndRecordsNothingWhenThePhoneCannotCount() = runBlocking {
        val app: PedometerApp = ApplicationProvider.getApplicationContext()
        val worker = TestListenableWorkerBuilder<StepCatchUpWorker>(app).build()

        assertEquals(ListenableWorker.Result.success(), worker.doWork())
        assertTrue(app.repository.days.first().isEmpty())
    }
}
