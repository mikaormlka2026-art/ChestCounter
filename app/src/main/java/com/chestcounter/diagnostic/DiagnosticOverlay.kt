package com.chestcounter.diagnostic

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import com.chestcounter.track.Track
import kotlin.math.max

class DiagnosticOverlay(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: BoxView? = null

    val isShown: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val v = BoxView(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
        }
        try {
            wm.addView(v, lp)
            view = v
        } catch (t: Throwable) {
            view = null
        }
    }

    fun update(tracks: List<Track>, frameW: Int, frameH: Int) {
        view?.setData(tracks, frameW, frameH)
    }

    fun hide() {
        view?.let {
            try {
                wm.removeView(it)
            } catch (t: Throwable) {
            }
        }
        view = null
    }

    private class BoxView(context: Context) : View(context) {

        private class Item(
            val box: BoundingBox,
            val label: String,
            val kind: DetectionKind,
            val visible: Boolean
        )

        private var items: List<Item> = emptyList()
        private var frameW = 0
        private var frameH = 0

        private val density = resources.displayMetrics.density
        private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.5f * density
        }
        private val labelBg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 12f * density
            typeface = Typeface.MONOSPACE
            color = 0xFF101010.toInt()
        }
        private val dash = DashPathEffect(floatArrayOf(10f * density, 8f * density), 0f)
        private val rect = RectF()

        fun setData(tracks: List<Track>, fw: Int, fh: Int) {
            items = tracks.map {
                Item(
                    it.box,
                    DiagnosticMapper.label(it.id, it.kind, it.score),
                    it.kind,
                    it.visible
                )
            }
            frameW = fw
            frameH = fh
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width
            val h = height
            if (w <= 0 || h <= 0) return
            val pad = 4f * density
            val labelH = textPaint.textSize + 2f * pad

            for (item in items) {
                val m = DiagnosticMapper.mapBox(item.box, frameW, frameH, w, h) ?: continue

                val base = if (item.kind == DetectionKind.CLOSED) GOLD else GREEN
                val color = if (item.visible) base else (base and 0x00FFFFFF) or 0x99000000.toInt()

                stroke.color = color
                stroke.pathEffect = if (item.visible) null else dash
                rect.set(m.left, m.top, m.right, m.bottom)
                canvas.drawRect(rect, stroke)

                val labelW = textPaint.measureText(item.label) + 2f * pad
                var top = m.top - labelH
                if (top < 0f) top = m.top
                var left = m.left
                if (left + labelW > w) left = max(0f, w - labelW)

                labelBg.color = color
                rect.set(left, top, left + labelW, top + labelH)
                canvas.drawRect(rect, labelBg)
                canvas.drawText(item.label, left + pad, top + pad - textPaint.ascent(), textPaint)
            }
        }

        private companion object {
            const val GOLD = 0xFFFFC107.toInt()
            const val GREEN = 0xFF00E676.toInt()
        }
    }
}
