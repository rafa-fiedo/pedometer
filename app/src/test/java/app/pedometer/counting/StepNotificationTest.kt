package app.pedometer.counting

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import app.pedometer.ui.MainActivity
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StepNotificationTest {
    /**
     * Android only brings the already-open app to the front when the tapped intent matches
     * the one the launcher icon used; any other intent stacks a second copy of the screen.
     */
    @Test
    fun tappingTheNotificationUsesTheSameIntentAsTheLauncherIcon() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        StepNotification.ensureChannel(context)

        val notification = StepNotification.build(context, steps = 6_482, goal = 10_000)

        val intent = shadowOf(notification.contentIntent).savedIntent
        assertEquals(Intent.ACTION_MAIN, intent.action)
        assertTrue(intent.hasCategory(Intent.CATEGORY_LAUNCHER))
        assertEquals(MainActivity::class.java.name, intent.component?.className)
    }
}
