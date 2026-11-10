package com.chestcounter.fusion

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.track.ChestTracker
import com.chestcounter.track.Track

data class EngineConfig(
    val minScore: Float = 0.7f,
    val conflictIou: Float = 0.3f
)

data class CountedChest(
    val trackId: Int,
    val confidence: Float
)

data class EngineOutput(
    val tracks: List<Track>,
    val events: List<PhaseEvent>,
    val newlyCounted: List<CountedChest>
)

class ChestEngine(
    private val cfg: EngineConfig = EngineConfig(),
    private val tracker: ChestTracker = ChestTracker(),
    private val phases: ChestPhaseTracker = ChestPhaseTracker()
) {
    var count: Int = 0
        private set
    var lastConfidence: Float = 0f
        private set

    private val counted = HashSet<Int>()
    private val closedPeak = HashMap<Int, Float>()

    fun process(
        detections: List<Detection>,
        nowMs: Long,
        goldScorer: (BoundingBox) -> Float
    ): EngineOutput {
        val clean = resolve(detections)
        val upd = tracker.update(clean, nowMs)

        val gold = HashMap<Int, Float>()
        for (t in upd.tracks) {
            if (t.kind == DetectionKind.CLOSED) {
                val old = closedPeak[t.id] ?: 0f
                if (t.score > old) closedPeak[t.id] = t.score
            }
            if (t.visible && phases.phaseOf(t.id) == ChestPhase.OPENED) {
                gold[t.id] = goldScorer(t.box)
            }
        }

        val events = phases.update(upd.tracks, gold, upd.lostIds, nowMs)

        val newly = ArrayList<CountedChest>()
        for (e in events) {
            if (e.to == ChestPhase.OPENED && e.sawClosed && counted.add(e.trackId)) {
                val open = upd.tracks.firstOrNull { it.id == e.trackId }?.score ?: 0f
                val closed = closedPeak[e.trackId] ?: open
                val conf = (closed + open) / 2f
                count++
                lastConfidence = conf
                newly.add(CountedChest(e.trackId, conf))
            }
        }

        for (id in upd.lostIds) {
            counted.remove(id)
            closedPeak.remove(id)
        }
        return EngineOutput(upd.tracks, events, newly)
    }

    fun reset() {
        tracker.reset()
        phases.reset()
        counted.clear()
        closedPeak.clear()
        count = 0
        lastConfidence = 0f
    }

    private fun resolve(items: List<Detection>): List<Detection> {
        val sorted = items
            .filter { it.score >= cfg.minScore }
            .sortedByDescending { it.score }
        val kept = ArrayList<Detection>()
        for (d in sorted) {
            if (kept.none { conflicts(it.box, d.box) }) kept.add(d)
        }
        return kept
    }

    private fun conflicts(a: BoundingBox, b: BoundingBox): Boolean {
        if (a.iou(b) > cfg.conflictIou) return true
        return contains(a, b.centerX, b.centerY) && contains(b, a.centerX, a.centerY)
    }

    private fun contains(box: BoundingBox, x: Float, y: Float): Boolean =
        x >= box.left && x <= box.right && y >= box.top && y <= box.bottom
}
