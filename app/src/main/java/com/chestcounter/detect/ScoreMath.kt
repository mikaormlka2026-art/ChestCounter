package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min

/** Formules de score en Kotlin pur (sans OpenCV) : testables sur JVM. */
object ScoreMath {

    fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x

    /** 0 sous lo, 1 au-dessus de hi, linéaire entre les deux. */
    fun ramp(x: Float, lo: Float, hi: Float): Float =
        if (hi <= lo) (if (x >= hi) 1f else 0f) else clamp01((x - lo) / (hi - lo))

    /** Trapèze : 0 hors de ]zeroLow, zeroHigh[, 1 entre oneLow et oneHigh. */
    fun band(x: Float, zeroLow: Float, oneLow: Float, oneHigh: Float, zeroHigh: Float): Float = when {
        x <= zeroLow || x >= zeroHigh -> 0f
        x < oneLow -> (x - zeroLow) / (oneLow - zeroLow)
        x <= oneHigh -> 1f
        else -> (zeroHigh - x) / (zeroHigh - oneHigh)
    }

    fun edgeScore(density: Float, c: ClassicalConfig): Float =
        band(density, c.edgeDensityMin, c.edgeDensityIdealMin, c.edgeDensityIdealMax, c.edgeDensityMax)

    /** Peu de petits fragments parasites = contour continu. */
    fun continuity(kept: Int, fragments: Int): Float =
        if (kept <= 0) 0f else 1f / (1f + 0.25f * max(0, fragments))

    fun contourScore(coverage: Float, continuity: Float, c: ClassicalConfig): Float =
        clamp01(
            0.6f * ramp(coverage, c.contourCoverageMin, c.contourCoverageFull) +
                0.4f * clamp01(continuity)
        )

    fun geometryScore(aspect: Float, extent: Float, solidity: Float, c: ClassicalConfig): Float {
        val a = band(aspect, c.aspectMin, c.aspectIdealMin, c.aspectIdealMax, c.aspectMax)
        return clamp01(0.4f * a + 0.3f * ramp(extent, 0.45f, 0.80f) + 0.3f * ramp(solidity, 0.65f, 0.90f))
    }

    /**
     * ratio : part de pixels de la couleur cible ; cellsCovered/cellsTotal : répartition sur une grille ;
     * darkRatio : part de pixels sombres (contours, bois). Une zone jaune uniforme est pénalisée.
     */
    fun hsvScore(ratio: Float, cellsCovered: Int, cellsTotal: Int, darkRatio: Float, c: ClassicalConfig): Float {
        if (cellsTotal <= 0) return 0f
        val ratioPart = band(ratio, 0.03f, 0.12f, 0.55f, 0.90f)
        val spreadPart = ramp(cellsCovered.toFloat() / cellsTotal, 0.2f, 0.6f)
        val dark = band(darkRatio, 0f, 0.05f, 0.45f, 0.80f)
        var s = (0.65f * ratioPart + 0.35f * spreadPart) * (0.5f + 0.5f * dark)
        if (ratio >= c.hsvFlatRatio) s *= c.hsvFlatPenalty
        return clamp01(s)
    }
}

/**
 * Comparaison de silhouettes par moments de Hu : distance = somme des écarts de log10 sur les
 * premiers invariants (plancher à 1e-7 pour ignorer le bruit numérique), similarité = exp(-d/échelle).
 */
object HuMath {
    const val FLOOR = 1e-7
    const val COMPONENTS = 4

    fun distance(a: DoubleArray, b: DoubleArray): Double? {
        val n = min(COMPONENTS, min(a.size, b.size))
        if (n == 0) return null
        var d = 0.0
        for (i in 0 until n) {
            if (a[i].isNaN() || b[i].isNaN()) return null
            d += abs(log10(max(abs(a[i]), FLOOR)) - log10(max(abs(b[i]), FLOOR)))
        }
        return d
    }

    fun similarity(a: DoubleArray, b: DoubleArray, scale: Double): Float? {
        val d = distance(a, b) ?: return null
        return exp(-d / max(scale, 1e-9)).toFloat()
    }
}
