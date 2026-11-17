package app.pedometer.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF

/** Ring thickness as a share of the circle's diameter; matches `round_widget_track.xml`. */
const val GOAL_RING_STROKE = 0.07f

/**
 * The round widget's progress arc as a white mask: it starts at the top and fills clockwise,
 * and everything else is transparent. The widget tints it with the theme colour, so the
 * launcher can recolour it after a light/dark switch, like the circle and track drawables.
 * A picture because Glance has no progress ring of its own.
 */
fun drawGoalArc(sizePx: Int, progress: Float): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val sweep = 360f * progress.coerceIn(0f, 1f)
    if (sweep <= 0f) return bitmap

    val stroke = sizePx * GOAL_RING_STROKE
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = Color.WHITE
        // Flat ends: round caps would close the ring visibly before the goal is reached.
        strokeCap = Paint.Cap.BUTT
    }
    val inset = stroke / 2f
    Canvas(bitmap).drawArc(RectF(inset, inset, sizePx - inset, sizePx - inset), -90f, sweep, false, paint)
    return bitmap
}
