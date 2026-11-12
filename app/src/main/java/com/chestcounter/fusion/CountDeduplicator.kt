package com.chestcounter.fusion

import com.chestcounter.detect.BoundingBox
import kotlin.math.hypot
import kotlin.math.max

/**
 * Anti-double comptage par position et par temps : un coffre compté il y a peu, au même endroit,
 * ne peut pas être compté une seconde fois, même si le suivi lui a donné un nouvel identifiant.
 * (Si la caméra a beaucoup bougé entre-temps, la position ne correspond plus : limite connue.)
 * Kotlin pur, testable sur JVM.
 */
class CountDeduplicator(
    private val windowMs: Long = 3000L,
    private val radiusFactor: Float = 0.75f,
    private val maxEntries: Int = 32
) {
    private class Entry(val cx: Float, val cy: Float, val size: Float, val tMs: Long)

    private val entries = ArrayList<Entry>()

    @Synchronized
    fun isDuplicate(box: BoundingBox, nowMs: Long): Boolean {
        prune(nowMs)
        val size = max(box.width, box.height)
        return entries.any { e ->
            val radius = radiusFactor * max(e.size, size)
            hypot(box.centerX - e.cx, box.centerY - e.cy) <= radius
        }
    }

    @Synchronized
    fun remember(box: BoundingBox, nowMs: Long) {
        entries.add(Entry(box.centerX, box.centerY, max(box.width, box.height), nowMs))
        while (entries.size > maxEntries) entries.removeAt(0)
    }

    @Synchronized
    fun reset() {
        entries.clear()
    }

    private fun prune(nowMs: Long) {
        val it = entries.iterator()
        while (it.hasNext()) {
            val e = it.next()
            if (nowMs < e.tMs || nowMs - e.tMs > windowMs) it.remove()
        }
    }
}
