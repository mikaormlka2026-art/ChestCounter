package com.chestcounter.detect

data class TransitionEvidence(
    /** Instant où le suivi est passé de « fermé » à « ouvert ». */
    val switchAtMs: Long,
    /** Plus fort changement mesuré autour du passage (0..1), null si rien n'était mesurable. */
    val strength: Float?,
    val samples: Int
)

/**
 * Historique par coffre suivi : cherche une TRANSITION cohérente (fermé -> ouvert accompagné d'un
 * changement visible) plutôt qu'une image isolée. Kotlin pur, testable sur JVM.
 */
class TemporalTransitions(
    private val windowMs: Long = 3000L,
    private val maxPerTrack: Int = 40
) {
    private class Obs(val tMs: Long, val kind: DetectionKind, val strength: Float?)

    private val map = HashMap<Int, ArrayList<Obs>>()

    @Synchronized
    fun record(trackId: Int, tMs: Long, kind: DetectionKind, scores: TemporalScores?) {
        val list = map.getOrPut(trackId) { ArrayList() }
        list.add(Obs(tMs, kind, strengthOf(scores)))
        while (list.size > maxPerTrack) list.removeAt(0)
    }

    private fun strengthOf(s: TemporalScores?): Float? {
        if (s == null) return null
        val a = s.structuralChangeScore
        val b = s.changeScore
        return when {
            a != null && b != null -> if (a > b) a else b
            a != null -> a
            else -> b
        }
    }

    @Synchronized
    fun evidence(trackId: Int, nowMs: Long): TransitionEvidence? {
        val list = map[trackId] ?: return null
        var switchIdx = -1
        for (i in list.indices.reversed()) {
            if (i > 0 && list[i].kind == DetectionKind.OPEN && list[i - 1].kind == DetectionKind.CLOSED) {
                switchIdx = i
                break
            }
        }
        if (switchIdx < 0) return null
        val switchAt = list[switchIdx].tMs
        if (nowMs - switchAt > windowMs) return null

        var best: Float? = null
        var samples = 0
        for (o in list) {
            if (o.tMs < switchAt - LEAD_MS || o.tMs > switchAt + windowMs) continue
            val s = o.strength ?: continue
            samples++
            if (best == null || s > best) best = s
        }
        return TransitionEvidence(switchAt, best, samples)
    }

    /**
     * Autorise le comptage. Sans exigence : toujours vrai. Avec exigence : faux seulement si un
     * changement a été MESURÉ et qu'il est trop faible ; sans historique ou sans mesure, on ne bloque pas.
     */
    fun gate(trackId: Int, nowMs: Long, required: Boolean, minStrength: Float): Boolean {
        if (!required) return true
        val ev = evidence(trackId, nowMs) ?: return true
        val s = ev.strength ?: return true
        return s >= minStrength
    }

    @Synchronized
    fun prune(nowMs: Long, maxAgeMs: Long = 10_000L) {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val list = it.next().value
            val last = if (list.isEmpty()) Long.MIN_VALUE else list[list.size - 1].tMs
            if (list.isEmpty() || nowMs < last || nowMs - last > maxAgeMs) it.remove()
        }
    }

    @Synchronized
    fun forget(trackId: Int) {
        map.remove(trackId)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    private companion object {
        /** Le changement visible peut précéder de peu le passage du modèle à « ouvert ». */
        const val LEAD_MS = 600L
    }
}
