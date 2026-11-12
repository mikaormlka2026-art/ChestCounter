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
    private val phases: ChestPhaseTracker = ChestPhaseTracker(),
    /** Anti-double comptage par position et par temps (null = désactivé). */
    private val dedup: CountDeduplicator? = null,
    /** Dernier contrôle avant de compter (par exemple, preuve temporelle) : (identifiant, instant) -> autorisé. */
    private val countGate: (Int, Long) -> Boolean = { _, _ -> true },
    /** Appelé à chaque reset() pour remettre à zéro les modules associés. */
    private val onReset: (() -> Unit)? = null
) {
    var count: Int = 0
        private set
    var lastConfidence: Float = 0f
        private set
    var suppressedDuplicates: Int = 0
        private set
    var gatedCounts: Int = 0
        private set

    private val counted = HashSet<Int>()
    private val closedPeak = HashMap<Int, Float>()

    fun phaseOf(trackId: Int): ChestPhase? = phases.phaseOf(trackId)

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
                val track = upd.tracks.firstOrNull { it.id == e.trackId }
                val open = track?.score ?: 0f
                val closed = closedPeak[e.trackId] ?: open
                val conf = (closed + open) / 2f

                if (!countGate(e.trackId, nowMs)) {
                    gatedCounts++
                    continue
                }
                if (dedup != null && track != null && dedup.isDuplicate(track.box, nowMs)) {
                    suppressedDuplicates++
                    continue
                }
                if (dedup != null && track != null) dedup.remember(track.box, nowMs)

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
        dedup?.reset()
        count = 0
        lastConfidence = 0f
        suppressedDuplicates = 0
        gatedCounts = 0
        onReset?.invoke()
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
