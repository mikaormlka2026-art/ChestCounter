package com.chestcounter.detect

data class ScoredBox(val box: BoundingBox, val score: Float)

data class RefinerConfig(
    val mergeIou: Float = 0.3f,
    val minSidePx: Float = 8f,
    val minAspect: Float = 0.4f,
    val maxAspect: Float = 2.5f
)

/**
 * Fusionne les régions proposées par plusieurs méthodes :
 * une seule boîte finale par coffre, bornée à l'image,
 * sans régions aberrantes (trop petites ou de proportions absurdes).
 */
class BoundingBoxRefiner(private val cfg: RefinerConfig = RefinerConfig()) {

    fun refine(input: List<ScoredBox>, frameWidth: Int, frameHeight: Int): List<ScoredBox> {
        if (frameWidth <= 0 || frameHeight <= 0) return emptyList()

        val usable = input
            .mapNotNull { clamp(it, frameWidth.toFloat(), frameHeight.toFloat()) }
            .filter { plausible(it.box) }
            .sortedByDescending { it.score }

        val groups = ArrayList<MutableList<ScoredBox>>()
        for (c in usable) {
            val g = groups.firstOrNull { grp -> grp.any { same(it.box, c.box) } }
            if (g != null) g.add(c) else groups.add(mutableListOf(c))
        }
        return groups.map { merge(it) }.sortedByDescending { it.score }
    }

    private fun clamp(s: ScoredBox, w: Float, h: Float): ScoredBox? {
        val b = BoundingBox(
            s.box.left.coerceIn(0f, w),
            s.box.top.coerceIn(0f, h),
            s.box.right.coerceIn(0f, w),
            s.box.bottom.coerceIn(0f, h)
        )
        return if (b.isValid()) ScoredBox(b, s.score) else null
    }

    private fun plausible(b: BoundingBox): Boolean {
        if (b.width < cfg.minSidePx || b.height < cfg.minSidePx) return false
        val aspect = b.width / b.height
        return aspect >= cfg.minAspect && aspect <= cfg.maxAspect
    }

    private fun same(a: BoundingBox, b: BoundingBox): Boolean {
        if (a.iou(b) > cfg.mergeIou) return true
        return contains(a, b.centerX, b.centerY) && contains(b, a.centerX, a.centerY)
    }

    private fun contains(b: BoundingBox, x: Float, y: Float): Boolean =
        x >= b.left && x <= b.right && y >= b.top && y <= b.bottom

    private fun merge(group: List<ScoredBox>): ScoredBox {
        if (group.size == 1) return group[0]
        var sw = 0f
        var l = 0f
        var t = 0f
        var r = 0f
        var bt = 0f
        for (g in group) {
            val w = g.score.coerceAtLeast(0.001f)
            sw += w
            l += g.box.left * w
            t += g.box.top * w
            r += g.box.right * w
            bt += g.box.bottom * w
        }
        return ScoredBox(
            BoundingBox(l / sw, t / sw, r / sw, bt / sw),
            group.maxOf { it.score }
        )
    }
}
