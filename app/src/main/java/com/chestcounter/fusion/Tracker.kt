package com.chestcounter.fusion

private class Cand(val trackId: Int, val di: Int, val iou: Float, val dist: Float)

class Track(val id: Int, var box: Box, var lastSeenMs: Long)

data class TrackResult(
    /** Identifiant attribué à chaque détection, dans l'ordre d'entrée. */
    val ids: List<Int>,
    val newIds: Set<Int>,
    /** Coffres suivis mais non vus dans cette image. */
    val unseen: List<Int>,
    /** Coffres oubliés (absents depuis plus de expireMs). */
    val expired: List<Int>,
)

/**
 * Suivi par association gloutonne IoU (meilleur recouvrement d'abord), avec une tolérance
 * sur la distance des centres pour absorber les mouvements de caméra. Un coffre perdu garde
 * son identifiant pendant expireMs, ce qui permet sa réapparition sans double comptage.
 */
class Tracker(
    private val iouMin: Float = 0.25f,
    private val gate: Float = 0.08f,
    private val expireMs: Long = 4000,
) {
    private val tracks = LinkedHashMap<Int, Track>()
    private var next = 1

    fun update(boxes: List<Box>, t: Long): TrackResult {
        val expired = tracks.values.filter { t - it.lastSeenMs > expireMs }.map { it.id }
        expired.forEach { tracks.remove(it) }

        val cands = ArrayList<Cand>()
        for ((id, tr) in tracks) for (di in boxes.indices) {
            val b = boxes[di]
            val iou = tr.box.iou(b)
            val d = tr.box.centerDist(b)
            val wr = b.w / tr.box.w; val hr = b.h / tr.box.h
            val similar = wr in 0.5f..2f && hr in 0.5f..2f
            if (iou >= iouMin || (d <= gate && similar)) cands += Cand(id, di, iou, d)
        }
        cands.sortWith(compareByDescending<Cand> { it.iou }.thenBy { it.dist })

        val ids = IntArray(boxes.size) { -1 }
        val matched = HashSet<Int>()
        for (c in cands) {
            if (c.trackId in matched || ids[c.di] != -1) continue
            ids[c.di] = c.trackId; matched += c.trackId
            tracks.getValue(c.trackId).apply { box = boxes[c.di]; lastSeenMs = t }
        }
        val newIds = HashSet<Int>()
        for (i in boxes.indices) if (ids[i] == -1) {
            val id = next++
            tracks[id] = Track(id, boxes[i], t)
            ids[i] = id; newIds += id
        }
        val unseen = tracks.keys.filter { it !in matched && it !in newIds }
        return TrackResult(ids.toList(), newIds, unseen, expired)
    }
}
