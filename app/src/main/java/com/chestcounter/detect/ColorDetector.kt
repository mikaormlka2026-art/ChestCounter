package com.chestcounter.detect

import android.graphics.Bitmap
import kotlin.math.max
import kotlin.math.min

class ColorDetector(
    private val params: GoldParams = GoldParams(),
    private val maxSamples: Int = 48,
    private val fullAt: Float = 0.25f
) {
    private var buf = IntArray(0)

    @Synchronized
    fun score(frame: Bitmap, box: BoundingBox): Float {
        val x0 = box.left.toInt().coerceIn(0, frame.width - 1)
        val y0 = box.top.toInt().coerceIn(0, frame.height - 1)
        val x1 = box.right.toInt().coerceIn(x0, frame.width)
        val y1 = box.bottom.toInt().coerceIn(y0, frame.height)
        val w = x1 - x0
        val h = y1 - y0
        if (w < 2 || h < 2) return 0f

        val need = w * h
        if (buf.size < need) buf = IntArray(need)
        frame.getPixels(buf, 0, w, x0, y0, w, h)

        val step = max(1, max(w, h) / maxSamples)
        val ratio = GoldAnalyzer.ratio(buf, w, h, step, params)
        return min(1f, ratio / fullAt)
    }
}
