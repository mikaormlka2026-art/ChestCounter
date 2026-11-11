package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Taille de la zone commune à deux images après un déplacement. */
class OverlapSize(val width: Int, val height: Int)

/** Résumé du flux optique : déplacement d'ensemble et mouvement local résiduel (en pixels). */
class FlowStats(val shiftX: Float, val shiftY: Float, val residualP75: Float)

/**
 * SSIM (similarité structurelle) en Kotlin pur, sur des images 8 bits en niveaux de gris.
 * Moyenne du SSIM de blocs win x win (fenêtre glissante, pas = stride).
 * 1 = identiques ; une valeur basse = structure modifiée.
 */
object Ssim {
    private const val C1 = 6.5025   // (0.01 * 255)^2
    private const val C2 = 58.5225  // (0.03 * 255)^2

    fun compute(a: ByteArray, b: ByteArray, w: Int, h: Int, win: Int = 8, stride: Int = 4): Float? {
        if (win < 2 || stride < 1 || w < win || h < win) return null
        if (a.size < w * h || b.size < w * h) return null
        val n = (win * win).toDouble()
        var sum = 0.0
        var count = 0
        var y = 0
        while (y + win <= h) {
            var x = 0
            while (x + win <= w) {
                var sa = 0.0
                var sb = 0.0
                var saa = 0.0
                var sbb = 0.0
                var sab = 0.0
                for (j in 0 until win) {
                    val row = (y + j) * w + x
                    for (i in 0 until win) {
                        val pa = (a[row + i].toInt() and 0xFF).toDouble()
                        val pb = (b[row + i].toInt() and 0xFF).toDouble()
                        sa += pa
                        sb += pb
                        saa += pa * pa
                        sbb += pb * pb
                        sab += pa * pb
                    }
                }
                val ma = sa / n
                val mb = sb / n
                val varA = saa / n - ma * ma
                val varB = sbb / n - mb * mb
                val cov = sab / n - ma * mb
                sum += ((2 * ma * mb + C1) * (2 * cov + C2)) /
                    ((ma * ma + mb * mb + C1) * (varA + varB + C2))
                count++
                x += stride
            }
            y += stride
        }
        return if (count == 0) null else (sum / count).toFloat().coerceIn(-1f, 1f)
    }
}

/** Formules de l'analyse temporelle en Kotlin pur (sans OpenCV) : testables sur JVM. */
object TemporalMath {

    /** Part de pixels changés -> note 0..1. */
    fun changeScore(changedRatio: Float, c: ClassicalConfig): Float =
        ScoreMath.ramp(changedRatio, c.changeRatioMin, c.changeRatioFull)

    /** SSIM -> changement structurel : 0 = identique, 1 = très différent. */
    fun structuralChangeScore(ssim: Float, c: ClassicalConfig): Float =
        ScoreMath.clamp01(1f - ScoreMath.ramp(ssim, c.ssimChanged, c.ssimSame))

    /** Mouvement local résiduel (pixels) -> note 0..1. */
    fun motionScore(residualPx: Float, c: ClassicalConfig): Float =
        ScoreMath.ramp(residualPx, 0f, c.flowResidualFullPx)

    /** Fiabilité du flux : part de points bien suivis, et nombre suffisant de points. */
    fun flowQuality(tracked: Int, candidates: Int, c: ClassicalConfig): Float {
        if (candidates <= 0 || tracked <= 0) return 0f
        val success = tracked.toFloat() / candidates
        val count = ScoreMath.ramp(tracked.toFloat(), c.flowMinTracked / 2f, c.flowMinTracked * 2f)
        return ScoreMath.clamp01(success * count)
    }

    /** Au-delà d'une certaine translation, la compensation du mouvement n'est plus fiable. */
    fun translationReliable(shiftX: Float, shiftY: Float, c: ClassicalConfig): Boolean =
        hypot(shiftX.toDouble(), shiftY.toDouble()) <= c.flowMaxTranslationPx

    /** Percentile approximatif (p entre 0 et 1) des n premières valeurs ; 0 si n <= 0. */
    fun percentile(values: FloatArray, n: Int, p: Float): Float {
        val m = minOf(n, values.size)
        if (m <= 0) return 0f
        val copy = values.copyOf(m)
        copy.sort()
        val idx = ((m - 1) * p.coerceIn(0f, 1f)).roundToInt().coerceIn(0, m - 1)
        return copy[idx]
    }

    /**
     * Déplacement d'ensemble = médiane des déplacements des points suivis (robuste aux points
     * qui bougent localement). Mouvement local résiduel = 75e percentile de l'écart de chaque point
     * à ce déplacement d'ensemble.
     */
    fun summarize(dx: FloatArray, dy: FloatArray, n: Int): FlowStats? {
        val m = minOf(n, dx.size, dy.size)
        if (m <= 0) return null
        val sx = percentile(dx, m, 0.5f)
        val sy = percentile(dy, m, 0.5f)
        val residual = FloatArray(m) { hypot((dx[it] - sx).toDouble(), (dy[it] - sy).toDouble()).toFloat() }
        return FlowStats(sx, sy, percentile(residual, m, 0.75f))
    }

    /**
     * Recadre deux images (w x h, 8 bits) sur leur zone commune après un déplacement (dx, dy) :
     * le pixel (x, y) de curr correspond au pixel (x - dx, y - dy) de prev.
     * Les tampons de sortie doivent avoir au moins w * h octets. Retourne null si la zone commune
     * est inférieure à minFraction de l'image.
     */
    fun alignOverlap(
        prev: ByteArray, curr: ByteArray, w: Int, h: Int, dx: Int, dy: Int,
        outPrev: ByteArray, outCurr: ByteArray, minFraction: Float = 0.5f
    ): OverlapSize? {
        if (w <= 0 || h <= 0) return null
        if (prev.size < w * h || curr.size < w * h || outPrev.size < w * h || outCurr.size < w * h) return null
        val x0 = maxOf(0, dx)
        val x1 = minOf(w, w + dx)
        val y0 = maxOf(0, dy)
        val y1 = minOf(h, h + dy)
        val ow = x1 - x0
        val oh = y1 - y0
        if (ow <= 0 || oh <= 0) return null
        if (ow.toLong() * oh < (minFraction * w * h)) return null
        for (y in y0 until y1) {
            for (x in x0 until x1) {
                val o = (y - y0) * ow + (x - x0)
                outCurr[o] = curr[y * w + x]
                outPrev[o] = prev[(y - dy) * w + (x - dx)]
            }
        }
        return OverlapSize(ow, oh)
    }

    /** Part des n premiers pixels dont l'écart de niveau de gris dépasse le seuil. */
    fun changedRatio(a: ByteArray, b: ByteArray, n: Int, threshold: Int): Float {
        val m = minOf(n, a.size, b.size)
        if (m <= 0) return 0f
        var changed = 0
        for (i in 0 until m) {
            val d = (a[i].toInt() and 0xFF) - (b[i].toInt() and 0xFF)
            if (d > threshold || -d > threshold) changed++
        }
        return changed.toFloat() / m
    }
}
