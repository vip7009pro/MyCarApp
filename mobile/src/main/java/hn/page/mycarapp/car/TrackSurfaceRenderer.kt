package hn.page.mycarapp.car

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.Surface
import androidx.car.app.SurfaceContainer
import hn.page.mycarapp.tracking.db.TrackPointEntity

class TrackSurfaceRenderer {
    private var surfaceContainer: SurfaceContainer? = null
    private var surface: Surface? = null

    private val backgroundPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val routePaint = Paint().apply {
        style = Paint.Style.STROKE
        color = Color.CYAN
        strokeWidth = 6f
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val startPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.GREEN
        isAntiAlias = true
    }

    private val endPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.RED
        isAntiAlias = true
    }

    fun onSurfaceAvailable(container: SurfaceContainer) {
        surfaceContainer = container
        surface?.release()
        surface = container.surface
    }

    fun onSurfaceDestroyed(container: SurfaceContainer) {
        if (surfaceContainer == container) {
            surfaceContainer = null
        }
        surface?.release()
        surface = null
    }

    fun render(points: List<TrackPointEntity>) {
        val s = surface ?: return
        val width = surfaceContainer?.width ?: 0
        val height = surfaceContainer?.height ?: 0
        if (width <= 0 || height <= 0) return

        val canvas = try {
            s.lockCanvas(null)
        } catch (_: Throwable) {
            return
        }

        try {
            draw(canvas, width, height, points)
        } finally {
            try {
                s.unlockCanvasAndPost(canvas)
            } catch (_: Throwable) {
            }
        }
    }

    private fun draw(canvas: Canvas, width: Int, height: Int, points: List<TrackPointEntity>) {
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)
        if (points.size < 2) return

        val lats = points.map { it.latitude }
        val lngs = points.map { it.longitude }
        var minLat = lats.minOrNull() ?: return
        var maxLat = lats.maxOrNull() ?: return
        var minLng = lngs.minOrNull() ?: return
        var maxLng = lngs.maxOrNull() ?: return

        // Avoid divide-by-zero if all points are the same.
        if (minLat == maxLat) {
            minLat -= 0.0001
            maxLat += 0.0001
        }
        if (minLng == maxLng) {
            minLng -= 0.0001
            maxLng += 0.0001
        }

        val padding = 24f
        val drawW = (width.toFloat() - padding * 2).coerceAtLeast(1f)
        val drawH = (height.toFloat() - padding * 2).coerceAtLeast(1f)

        fun project(p: TrackPointEntity): PointF {
            val x = ((p.longitude - minLng) / (maxLng - minLng)).toFloat() * drawW + padding
            val yNorm = ((p.latitude - minLat) / (maxLat - minLat)).toFloat()
            val y = (1f - yNorm) * drawH + padding
            return PointF(x, y)
        }

        var prev = project(points.first())
        for (i in 1 until points.size) {
            val cur = project(points[i])
            canvas.drawLine(prev.x, prev.y, cur.x, cur.y, routePaint)
            prev = cur
        }

        val start = project(points.first())
        val end = project(points.last())
        canvas.drawCircle(start.x, start.y, 10f, startPaint)
        canvas.drawCircle(end.x, end.y, 10f, endPaint)
    }
}
