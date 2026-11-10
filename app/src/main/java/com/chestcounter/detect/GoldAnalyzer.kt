package com.chestcounter.detect

data class GoldParams(
    val hueMin: Float = 35f,
    val hueMax: Float = 60f,
    val satMin: Float = 0.45f,
    val valMin: Float = 0.5f
)

object GoldAnalyzer {

    fun isGold(pixel: Int, p: GoldParams): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        val mx = maxOf(r, g, b)
        val mn = minOf(r, g, b)
        if (mx == 0) return false
        val delta = mx - mn
        if (delta == 0) return false

        val sat = delta.toFloat() / mx
        val v = mx / 255f
        if (sat < p.satMin || v < p.valMin) return false

        var hue = when (mx) {
            r -> 60f * ((g - b).toFloat() / delta)
            g -> 60f * ((b - r).toFloat() / delta + 2f)
            else -> 60f * ((r - g).toFloat() / delta + 4f)
        }
        if (hue < 0f) hue += 360f
        return hue >= p.hueMin && hue <= p.hueMax
    }

    fun ratio(
        pixels: IntArray,
        width: Int,
        height: Int,
        step: Int,
        p: GoldParams = GoldParams()
    ): Float {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return 0f
        val s = if (step < 1) 1 else step
        var total = 0
        var gold = 0
        var y = 0
        while (y < height) {
            var x = 0
            while (x < width) {
                total++
                if (isGold(pixels[y * width + x], p)) gold++
                x += s
            }
            y += s
        }
        return if (total == 0) 0f else gold.toFloat() / total
    }
}
