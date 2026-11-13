package com.chestcounter.fusion

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.track.ChestTracker
import com.chestcounter.track.Track

data class EngineConfig(
    val minScore: Float = 0.7f,
    val conflictIou: Float = 0.3f,
    /** Délai pendant lequel une ouverture refusée par le contrôle reste en attente avant abandon. */
    val pendingGateMs: Long = 3000L
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
    /**
     * Dernier contrôle avant de compter : (identifiant, instant) -> autorisé.
     * Un refus n'est PAS définitif : l'ouverture reste en attente cfg.pendingGateMs et le contrôle
     * est rappelé aux images suivantes.
     */
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

    /** Ouvertures refusées au moins une fois par le contrôle (même si acceptées plus tard). */
    var gatedCounts: Int = 0
        private set

    /** Ouvertures en attente abandonnées sans jamais avoir été validées (non comptées). */
    var expiredPending: Int = 0
        private set

    val pendingCount: Int get() = pendingGate.size

    private val counted = HashSet<Int>()
    private val closedPeak = HashMap<Int, Float>()

    /** Identifiant de piste -> instant de l'événement OPENED refusé par le contrôle. */
    private val pendingGate = HashMap<Int, Long>()

    private enum class Attempt { COUNTED, DUPLICATE, GATED }

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

        // 1) Réévaluation des ouvertures en attente, aux images STRICTEMENT postérieures à l'événement.
        if (pendingGate.isNotEmpty()) {
            val ids = ArrayList(pendingGate.keys)
            ids.sort()
            for (id in ids) {
                val eventMs = pendingGate[id] ?: continue
                if (eventMs >= nowMs) continue
                val alive = upd.tracks.any { it.id == id }
                if (!alive || nowMs - eventMs > cfg.pendingGateMs) {
                    pendingGate.remove(id)
                    expiredPending++
                    continue
                }
                if (attempt(id, nowMs, upd.tracks, newly) != Attempt.GATED) pendingGate.remove(id)
            }
        }

        // 2) Nouvelles ouvertures : fermé observé, puis ouvert stable.
        for (e in events) {
            if (e.to == ChestPhase.OPENED && e.sawClosed &&
                e.trackId !in counted && e.trackId !in pendingGate
            ) {
                if (attempt(e.trackId, nowMs, upd.tracks, newly) == Attempt.GATED) {
                    gatedCounts++
                    pendingGate[e.trackId] = nowMs
                }
            }
        }

        for (id in upd.lostIds) {
            counted.remove(id)
            closedPeak.remove(id)
            if (pendingGate.remove(id) != null) expiredPending++
        }
        return EngineOutput(upd.tracks, events, newly)
    }

    /** Contrôle d'abord, doublon ensuite, et seulement alors l'identifiant est marqué comme compté. */
    private fun attempt(
        id: Int,
        nowMs: Long,
        tracks: List<Track>,
        newly: MutableList<CountedChest>
    ): Attempt {
        if (!countGate(id, nowMs)) return Attempt.GATED

        val track = tracks.firstOrNull { it.id == id }
        if (dedup != null && track != null && dedup.isDuplicate(track.box, nowMs)) {
            suppressedDuplicates++
            counted.add(id)
            return Attempt.DUPLICATE
        }
        if (dedup != null && track != null) dedup.remember(track.box, nowMs)

        val open = track?.score ?: 0f
        val closed = closedPeak[id] ?: open
        val conf = (closed + open) / 2f

        counted.add(id)
        count++
        lastConfidence = conf
        newly.add(CountedChest(id, conf))
        return Attempt.COUNTED
    }

    fun reset() {
        tracker.reset()
        phases.reset()
        counted.clear()
        closedPeak.clear()
        pendingGate.clear()
        dedup?.reset()
        count = 0
        lastConfidence = 0f
        suppressedDuplicates = 0
        gatedCounts = 0
        expiredPending = 0
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
