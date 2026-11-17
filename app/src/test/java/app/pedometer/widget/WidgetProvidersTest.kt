package app.pedometer.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.content.res.XmlResourceParser
import android.util.Xml
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.test.core.app.ApplicationProvider
import app.pedometer.R
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.xmlpull.v1.XmlPullParser
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The widget picker lists one entry per starting size, 1 to 4 cells wide, all drawing the same
 * widget and all still resizable.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WidgetProvidersTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private data class Entry(val receiver: Class<out GlanceAppWidgetReceiver>, val xml: Int, val cells: Int)

    private val entries = listOf(
        Entry(PedometerWidget1x1Receiver::class.java, R.xml.pedometer_widget_1x1, 1),
        // The original receiver stays the 2 x 1 entry, so widgets already on home screens keep working.
        Entry(PedometerWidgetReceiver::class.java, R.xml.pedometer_widget_info, 2),
        Entry(PedometerWidget3x1Receiver::class.java, R.xml.pedometer_widget_3x1, 3),
        Entry(PedometerWidget4x1Receiver::class.java, R.xml.pedometer_widget_4x1, 4),
    )

    private fun providerTag(xml: Int): XmlResourceParser =
        context.resources.getXml(xml).apply {
            while (next() != XmlPullParser.START_TAG) Unit
        }

    private fun intAttr(xml: Int, attr: Int): Int {
        val typed = context.resources.obtainAttributes(Xml.asAttributeSet(providerTag(xml)), intArrayOf(attr))
        return try { typed.getInt(0, -1) } finally { typed.recycle() }
    }

    private fun dpAttr(xml: Int, attr: Int): Float {
        val typed = context.resources.obtainAttributes(Xml.asAttributeSet(providerTag(xml)), intArrayOf(attr))
        return try { typed.getDimension(0, -1f) / context.resources.displayMetrics.density } finally { typed.recycle() }
    }

    @Test
    fun eachSizeStartsAtItsOwnWidth() {
        for (e in entries) {
            assertEquals(e.cells, intAttr(e.xml, android.R.attr.targetCellWidth), "target cells for ${e.cells} x 1")
            assertEquals(1, intAttr(e.xml, android.R.attr.targetCellHeight), "target height for ${e.cells} x 1")
            // Android 11 and older place widgets by minWidth: 70 x cells - 30 dp.
            assertEquals(70f * e.cells - 30f, dpAttr(e.xml, android.R.attr.minWidth), "minWidth for ${e.cells} x 1")
        }
    }

    @Test
    fun everySizeCanStillBeResizedFromOneToFourCells() {
        for (e in entries) {
            assertEquals(40f, dpAttr(e.xml, android.R.attr.minResizeWidth), "minResizeWidth for ${e.cells} x 1")
            assertEquals(276f, dpAttr(e.xml, android.R.attr.maxResizeWidth), "maxResizeWidth for ${e.cells} x 1")
        }
    }

    @Test
    fun allEntriesDrawTheSameWidget() {
        for (e in entries) {
            val receiver = e.receiver.getDeclaredConstructor().newInstance()
            assertTrue(receiver.glanceAppWidget is PedometerWidget, "${e.receiver.simpleName} draws PedometerWidget")
        }
    }

    @Test
    fun roundEntryIsAFixedOneByOne() {
        val xml = R.xml.pedometer_widget_round
        assertEquals(1, intAttr(xml, android.R.attr.targetCellWidth))
        assertEquals(1, intAttr(xml, android.R.attr.targetCellHeight))
        assertEquals(0, intAttr(xml, android.R.attr.resizeMode))
    }

    @Test
    fun roundReceiverDrawsTheRoundWidget() =
        assertTrue(RoundStepsWidgetReceiver().glanceAppWidget is RoundStepsWidget)

    @Test
    fun thePickerListsFiveEntries() {
        val receivers = context.packageManager
            .queryBroadcastReceivers(Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setPackage(context.packageName), 0)
            .map { it.activityInfo.name }
            .toSet()
        assertEquals(entries.map { it.receiver.name }.toSet() + RoundStepsWidgetReceiver::class.java.name, receivers)
    }
}
