package com.chestcounter.track

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind

data class Track(
    val id: Int,
    val kind: DetectionKind,
    val box: BoundingBox,
    val score: Float,
    val firstSeenMs: Long,
    val lastSeenMs: Long,
    val hits: Int,
    val visible: Boolean
)

data class TrackerConfig(
    val minIou: Float = 0.3f,
    val minHits: Int = 2,
    val maxLostMs: Long = 1500L,
    val smoothing: Float = 0.5f
)

data class TrackerUpdate(
    val tracks: List<Track>,
    val lostIds: List<Int>
)

class ChestTracker(private val cfg: TrackerConfig = TrackerConfig()) {

    private class Entry(
        val id: Int,
        var kind: DetectionKind,
        var box: BoundingBox,
        var score: Float,
        val firstSeenMs: Long,
        var lastSeenMs: Long,
        var hits: Int,
        var confirmed: Boolean
    )

    private val entries = ArrayList<Entry>()
    private var nextId = 1

    fun update(detections: List<Detection>, nowMs: Long): TrackerUpdate {
        val pairs = ArrayList<Triple<Float, Int, Int>>()
        for (ei in entries.indices) {
            for (di in detections.indices) {
                val a = affinity(entries[ei].box, detections[di].box)
                if (a > 0f) pairs.add(Triple(a, ei, di))
            }
        }
        pairs.sortByDescending { it.first }

        val usedEntry = BooleanArray(entries.size)
        val usedDetection = BooleanArray(detections.size)
        for ((_, ei, di) in pairs) {
            if (usedEntry[ei] || usedDetection[di]) continue
            usedEntry[ei] = true
            usedDetection[di] = true
            val e = entries[ei]
            val d = detections[di]
            e.box = blend(e.box, d.box)
            e.kind = d.kind
            e.score = d.score
            e.lastSeenMs = nowMs
            e.hits++
            if (e.hits >= cfg.minHits) e.confirmed = true
        }

        for (di in detections.indices) {
            if (usedDetection[di]) continue
            val d = detections[di]
            entries.add(
                Entry(
                    nextId++, d.kind, d.box, d.score,
                    nowMs, nowMs, 1, cfg.minHits <= 1
                )
            )
        }

        val lost = ArrayList<Int>()
        val iter = entries.iterator()
        while (iter.hasNext()) {
            val e = iter.next()
            if (nowMs - e.lastSeenMs > cfg.maxLostMs) {
                if (e.confirmed) lost.add(e.id)
                iter.remove()
            }
        }

        val tracks = entries
            .filter { it.confirmed }
            .map {
                Track(
                    it.id, it.kind, it.box, it.score,
                    it.firstSeenMs, it.lastSeenMs, it.hits,
                    it.lastSeenMs == nowMs
                )
            }
        return TrackerUpdate(tracks, lost)
    }

    fun reset() {
        entries.clear()
        nextId = 1
    }

    private fun affinity(a: BoundingBox, b: BoundingBox): Float {
        val iou = a.iou(b)
        if (iou >= cfg.minIou) return iou
        if (contains(a, b.centerX, b.centerY) && contains(b, a.centerX, a.centerY)) {
            return cfg.minIou
        }
        return 0f
    }

    private fun contains(box: BoundingBox, x: Float, y: Float): Boolean =
        x >= box.left && x <= box.right && y >= box.top && y <= box.bottom

    private fun blend(old: BoundingBox, new: BoundingBox): BoundingBox {
        val s = cfg.smoothing
        fun mix(a: Float, b: Float) = a + (b - a) * s
        return BoundingBox(
            mix(old.left, new.left),
            mix(old.top, new.top),
            mix(old.right, new.right),
            mix(old.bottom, new.bottom)
        )
    }
}
