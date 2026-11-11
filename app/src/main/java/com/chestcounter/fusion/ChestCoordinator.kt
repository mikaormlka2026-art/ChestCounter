package com.chestcounter.fusion

/** Relie suivi, fusion et machines d'états : c'est le seul endroit qui incrémente le total. */
class ChestCoordinator(
    private val fusion: FusionEngine = FusionEngine(),
    private val tracker: Tracker = Tracker(),
    private val lcfg: LifecycleConfig = LifecycleConfig(),
) {
    private val buffers = HashMap<Int, ArrayList<Evidence>>()
    private val lifecycles = HashMap<Int, ChestLifecycle>()
    private val globals = ArrayList<Evidence>()
    private val last = HashMap<Int, FusionResult>()
    var total = 0; private set

    /** Indice sans boîte (ex. audio) : fusionné avec tous les coffres suivis. */
    fun addGlobalEvidence(e: Evidence) { globals += e }
    fun states(): Map<Int, ChestState> = lifecycles.mapValues { it.value.state }
    fun lastResult(id: Int): FusionResult? = last[id]

    fun process(f: FrameInput): List<ChestEvent> {
        val events = ArrayList<ChestEvent>()
        val tr = tracker.update(f.detections.map { it.box }, f.tMs)
        globals.removeAll { f.tMs - it.tMs > fusion.config.maxAgeMs }

        f.detections.forEachIndexed { i, det ->
            val id = tr.ids[i]
            val buf = buffers.getOrPut(id) { ArrayList() }
            det.evidences.forEach { buf += it.copy(chestId = id) }
            buf.removeAll { f.tMs - it.tMs > fusion.config.maxAgeMs * 3 }
            while (buf.size > 64) buf.removeAt(0)
            val r = fusion.fuse(buf + globals, f.tMs, f.unavailable)
            last[id] = r
            val ev = lifecycles.getOrPut(id) { ChestLifecycle(id, lcfg) }.update(r, f.tMs)
            if (ev is ChestEvent.Counted) total++
            if (ev != ChestEvent.None) events += ev
        }
        for (id in tr.unseen) {
            val ev = lifecycles[id]?.update(null, f.tMs) ?: continue
            if (ev != ChestEvent.None) events += ev
        }
        for (id in tr.expired) {
            lifecycles.remove(id)?.let { events += it.expire() }
            buffers.remove(id); last.remove(id)
        }
        return events
    }
}
