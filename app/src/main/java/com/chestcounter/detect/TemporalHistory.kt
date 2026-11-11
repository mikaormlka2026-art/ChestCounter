package com.chestcounter.detect

/** Dernière observation (ROI normalisée, niveaux de gris 8 bits) d'un coffre suivi. */
class RoiSnapshot(
    var tMs: Long,
    var width: Int,
    var height: Int,
    val data: ByteArray
)

/**
 * Historique d'une observation par coffre suivi (clé = identifiant de suivi).
 * Kotlin pur, testable sur JVM. Les tableaux sont réutilisés d'une mise à jour à l'autre :
 * ce que get() renvoie doit être lu AVANT l'appel suivant à put() pour la même clé.
 * Un historique trop ancien, ou dont l'horloge a reculé, n'est pas renvoyé.
 */
class TemporalHistory(
    private val maxGapMs: Long,
    private val maxTracks: Int = 16
) {
    private val map = LinkedHashMap<Int, RoiSnapshot>()

    @Synchronized
    fun get(trackId: Int, nowMs: Long): RoiSnapshot? {
        val s = map[trackId] ?: return null
        if (nowMs < s.tMs || nowMs - s.tMs > maxGapMs) return null
        return s
    }

    @Synchronized
    fun put(trackId: Int, tMs: Long, width: Int, height: Int, src: ByteArray) {
        val n = width * height
        if (width <= 0 || height <= 0 || src.size < n) return
        val old = map.remove(trackId)
        val snap = if (old != null && old.data.size == n) old else RoiSnapshot(tMs, width, height, ByteArray(n))
        snap.tMs = tMs
        snap.width = width
        snap.height = height
        System.arraycopy(src, 0, snap.data, 0, n)
        map[trackId] = snap
        while (map.size > maxTracks) {
            val oldest = map.keys.iterator().next()
            map.remove(oldest)
        }
    }

    /** Oublie les coffres dont la dernière observation est trop ancienne. */
    @Synchronized
    fun prune(nowMs: Long) {
        val it = map.entries.iterator()
        while (it.hasNext()) {
            val s = it.next().value
            if (nowMs < s.tMs || nowMs - s.tMs > maxGapMs) it.remove()
        }
    }

    @Synchronized
    fun remove(trackId: Int) {
        map.remove(trackId)
    }

    @Synchronized
    fun clear() {
        map.clear()
    }

    @Synchronized
    fun size(): Int = map.size
}
