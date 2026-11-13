package com.chestcounter.analysis

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/** Valeurs de départ NON calibrées. */
data class ReconstructionConfig(
    val minClosedObs: Int = 3,
    val minClosedSpanMs: Long = 300L,
    val minOpenObs: Int = 2,
    val minOpenSpanMs: Long = 300L,
    val maxSwitchGapMs: Long = 1500L,
    val dedupWindowMs: Long = 3000L,
    val dedupRadiusFactor: Float = 0.75f
)

/** Fonction pure et déterministe : mêmes observations -> mêmes résultats. */
class OpeningReconstructor(private val cfg: ReconstructionConfig = ReconstructionConfig()) {

    private class Run(val kind: DetectionKind, val from: Int, var to: Int)

    fun reconstruct(obs: List<ObsPoint>): List<Reconstructed> {
        val groups = obs.groupBy { Pair(it.epoch, it.trackId) }
        val keys = groups.keys.sortedWith(compareBy<Pair<Int, Int>>({ it.first }, { it.second }))
        val raw = ArrayList<Reconstructed>()
        for (k in keys) {
            raw.add(analyseTrack(k.first, k.second, groups.getValue(k).sortedBy { it.tMs }))
        }
        return dedupe(raw)
    }

    private fun runsOf(list: List<ObsPoint>): List<Run> {
        val runs = ArrayList<Run>()
        for (i in list.indices) {
            val last = runs.lastOrNull()
            if (last != null && last.kind == list[i].kind) last.to = i
            else runs.add(Run(list[i].kind, i, i))
        }
        return runs
    }

    private fun analyseTrack(epoch: Int, id: Int, list: List<ObsPoint>): Reconstructed {
        val first = list.first()
        val runs = runsOf(list)
        val firstRunSize = runs[0].to - runs[0].from + 1

        fun rec(
            box: BoundingBox, lastClosed: Long?, firstOpen: Long?,
            closedN: Int, openN: Int, out: ReconOutcome, why: String
        ) = Reconstructed(epoch, id, box, lastClosed, firstOpen, closedN, openN, out, why)

        if (runs[0].kind == DetectionKind.OPEN) {
            return rec(
                first.box, null, first.tMs, 0, firstRunSize,
                ReconOutcome.ALREADY_OPEN_ON_FIRST_OBSERVATION, "ouvert dès la première observation"
            )
        }

        var failure: String? = null
        for (k in 1 until runs.size) {
            val o = runs[k]
            val c = runs[k - 1]
            if (o.kind != DetectionKind.OPEN || c.kind != DetectionKind.CLOSED) continue

            val closedN = c.to - c.from + 1
            val openN = o.to - o.from + 1
            val closedSpan = list[c.to].tMs - list[c.from].tMs
            val openSpan = list[o.to].tMs - list[o.from].tMs
            val gap = list[o.from].tMs - list[c.to].tMs
            val why = when {
                closedN < cfg.minClosedObs || closedSpan < cfg.minClosedSpanMs ->
                    "état fermé trop peu observé ($closedN obs, $closedSpan ms)"
                openN < cfg.minOpenObs || openSpan < cfg.minOpenSpanMs ->
                    "état ouvert non stable ($openN obs, $openSpan ms)"
                gap > cfg.maxSwitchGapMs ->
                    "transition non continue (lacune de $gap ms)"
                else -> null
            }
            if (why != null) {
                if (failure == null) failure = why
                continue
            }

            val box = list[o.from].box
            val lastClosed = list[c.to].tMs
            val firstOpen = list[o.from].tMs
            val flipFlop = runs.drop(k + 1).any {
                it.kind == DetectionKind.CLOSED && it.to - it.from + 1 >= cfg.minClosedObs
            }
            return if (flipFlop) {
                rec(box, lastClosed, firstOpen, closedN, openN, ReconOutcome.INVALID,
                    "alternance fermé/ouvert après l'ouverture")
            } else {
                rec(box, lastClosed, firstOpen, closedN, openN, ReconOutcome.OPENED,
                    "transition fermé → ouvert observée")
            }
        }

        return if (failure != null) {
            rec(first.box, null, null, firstRunSize, 0, ReconOutcome.INVALID, failure)
        } else {
            rec(first.box, null, null, firstRunSize, 0, ReconOutcome.NOT_OPENED, "jamais vu ouvert")
        }
    }

    private fun dedupe(items: List<Reconstructed>): List<Reconstructed> {
        val ordered = items.sortedWith(
            compareBy<Reconstructed>({ it.firstOpenMs ?: Long.MAX_VALUE }, { it.epoch }, { it.trackId })
        )
        val kept = ArrayList<Reconstructed>()
        val out = ArrayList<Reconstructed>()
        for (r in ordered) {
            if (r.outcome != ReconOutcome.OPENED) {
                out.add(r)
                continue
            }
            val dup = kept.firstOrNull { isDuplicate(it, r) }
            if (dup != null) {
                out.add(r.copy(outcome = ReconOutcome.INVALID, reason = "doublon de la piste ${dup.trackId}"))
            } else {
                kept.add(r)
                out.add(r)
            }
        }
        return out
    }

    private fun isDuplicate(a: Reconstructed, b: Reconstructed): Boolean {
        val ta = a.firstOpenMs ?: return false
        val tb = b.firstOpenMs ?: return false
        if (abs(ta - tb) > cfg.dedupWindowMs) return false
        val size = max(max(a.box.width, a.box.height), max(b.box.width, b.box.height))
        val d = hypot(a.box.centerX - b.box.centerX, a.box.centerY - b.box.centerY)
        return d <= cfg.dedupRadiusFactor * size
    }
}
