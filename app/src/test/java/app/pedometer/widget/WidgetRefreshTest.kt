package app.pedometer.widget

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

// Robolectric so the failure path can log through android.util.Log.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetRefreshTest {
    private val calls = mutableListOf<String>()

    private fun refresh(canCount: Boolean, reading: () -> Boolean) = runBlocking {
        refreshSteps(
            canCount = canCount,
            recordOnce = {
                calls += "record"
                reading()
            },
            redraw = { calls += "redraw" },
        )
    }

    @Test
    fun readsThenRedraws() {
        refresh(canCount = true) { true }
        assertEquals(listOf("record", "redraw"), calls)
    }

    @Test
    fun redrawsEvenWithoutAReading() {
        refresh(canCount = true) { false }
        assertEquals(listOf("record", "redraw"), calls)
    }

    @Test
    fun redrawsWhenReadingFails() {
        refresh(canCount = true) { throw IllegalStateException("sensor gone") }
        assertEquals(listOf("record", "redraw"), calls)
    }

    @Test
    fun withoutPermissionOnlyRedraws() {
        refresh(canCount = false) { true }
        assertEquals(listOf("redraw"), calls)
    }
}
