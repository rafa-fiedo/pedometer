package app.pedometer.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF

/**
 * The round widget's background: a filled circle with a goal ring just inside its edge. The
 * ring starts at the top and fills clockwise; everything outside the circle stays transparent.
 * Drawn as a picture because Glance has no progress ring of its own.
 */
fun drawGoalRing(sizePx: Int, progress: Float, circleColor: Int, trackColor: Int, ringColor: Int): Bitmap {
    val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val centre = sizePx / 2f
    val stroke = sizePx * 0.07f

    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = circleColor
    }
    canvas.drawCircle(centre, centre, centre, fill)

    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = stroke
        color = trackColor
    }
    canvas.drawCircle(centre, centre, centre - stroke / 2f, line)

    val sweep = 360f * progress.coerceIn(0f, 1f)
    // A zero sweep with round caps would still draw a dot at the top.
    if (sweep > 0f) {
        line.color = ringColor
        line.strokeCap = Paint.Cap.ROUND
        val inset = stroke / 2f
        canvas.drawArc(RectF(inset, inset, sizePx - inset, sizePx - inset), -90f, sweep, false, line)
    }
    return bitmap
}
