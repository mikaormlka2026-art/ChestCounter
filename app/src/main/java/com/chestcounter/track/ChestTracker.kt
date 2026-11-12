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
    val visible: Boolean,
    /** Vitesse estimée du centre, en pixels par seconde. */
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    /** Nombre de mises à jour du suivi depuis la création de la piste. */
    val age: Int = 0,
    /** Images consécutives sans détection associée. */
    val missedFrames: Int = 0
)

data class TrackerConfig(
    val minIou: Float = 0.3f,
    val minHits: Int = 2,
    val maxLostMs: Long = 1500L,
    val smoothing: Float = 0.5f,
    /** Associe les détections à la position PRÉDITE par Kalman (utile quand la caméra bouge). */
    val useKalman: Boolean = true,
    val kalman: KalmanConfig = KalmanConfig(),
    /** Plafond de l'extrapolation d'une piste non vue. */
    val maxPredictMs: Long = 600L,
    /** true : la boîte publiée est la boîte filtrée par Kalman au lieu du lissage simple. */
    val kalmanSmoothsBox: Boolean = false
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
        var confirmed: Boolean,
        val kalman: KalmanBoxFilter,
        var age: Int = 1,
        var missed: Int = 0
    )

    private val entries = ArrayList<Entry>()
    private var nextId = 1

    fun update(detections: List<Detection>, nowMs: Long): TrackerUpdate {
        val pairs = ArrayList<Triple<Float, Int, Int>>()
        for (ei in entries.indices) {
            val ref = reference(entries[ei], nowMs)
            for (di in detections.indices) {
                val a = affinity(ref, detections[di].box)
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
            e.kalman.update(d.box, nowMs)
            e.box = if (cfg.useKalman && cfg.kalmanSmoothsBox) e.kalman.box() else blend(e.box, d.box)
            e.kind = d.kind
            e.score = d.score
            e.lastSeenMs = nowMs
            e.hits++
            e.age++
            e.missed = 0
            if (e.hits >= cfg.minHits) e.confirmed = true
        }

        for (ei in entries.indices) {
            if (!usedEntry[ei]) {
                entries[ei].missed++
                entries[ei].age++
            }
        }

        for (di in detections.indices) {
            if (usedDetection[di]) continue
            val d = detections[di]
            entries.add(
                Entry(
                    nextId++, d.kind, d.box, d.score,
                    nowMs, nowMs, 1, cfg.minHits <= 1,
                    KalmanBoxFilter(d.box, nowMs, cfg.kalman)
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
                val visible = it.lastSeenMs == nowMs
                val box = if (!visible && cfg.useKalman) {
                    it.kalman.predictedBox(nowMs, cfg.maxPredictMs)
                } else {
                    it.box
                }
                Track(
                    it.id, it.kind, box, it.score,
                    it.firstSeenMs, it.lastSeenMs, it.hits,
                    visible,
                    it.kalman.vx, it.kalman.vy, it.age, it.missed
                )
            }
        return TrackerUpdate(tracks, lost)
    }

    fun reset() {
        entries.clear()
        nextId = 1
    }

    /** Boîte de référence pour l'association : position prédite si Kalman est actif. */
    private fun reference(e: Entry, nowMs: Long): BoundingBox =
        if (cfg.useKalman) e.kalman.predictedBox(nowMs, cfg.maxPredictMs) else e.box

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
