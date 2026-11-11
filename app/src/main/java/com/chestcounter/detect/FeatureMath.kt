package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import kotlin.math.sqrt

/** Formules de score HOG et ORB en Kotlin pur (sans OpenCV) : testables sur JVM. */
object FeatureMath {

    /** Similarité cosinus entre deux descripteurs. null si tailles différentes ou vecteur nul. */
    fun cosine(a: FloatArray, b: FloatArray): Float? {
        if (a.isEmpty() || a.size != b.size) return null
        var dot = 0.0
        var na = 0.0
        var nb = 0.0
        for (i in a.indices) {
            val x = a[i].toDouble()
            val y = b[i].toDouble()
            dot += x * y
            na += x * x
            nb += y * y
        }
        if (na <= 1e-12 || nb <= 1e-12) return null
        return (dot / sqrt(na * nb)).toFloat().coerceIn(-1f, 1f)
    }

    fun hogScore(cosine: Float, c: ClassicalConfig): Float =
        ScoreMath.ramp(cosine, c.hogCosMin, c.hogCosFull)

    /**
     * Test du ratio de Lowe : une correspondance n'est gardée que si la meilleure distance est
     * nettement meilleure que la deuxième. Sur des textures répétitives, les deux distances sont
     * proches et la correspondance est rejetée.
     */
    fun passesRatioTest(best: Float, second: Float, ratio: Float): Boolean =
        best <= ratio * second

    /** Part des correspondances compatibles avec une même transformation géométrique. */
    fun geometricConsistency(goodMatches: Int, inliers: Int?): Float? {
        if (inliers == null || goodMatches <= 0) return null
        return (inliers.toFloat() / goodMatches).coerceIn(0f, 1f)
    }

    /**
     * goodMatches : correspondances après test du ratio ; inliers : correspondances cohérentes
     * après estimation géométrique (null si trop peu de points pour l'estimer).
     * Sans validation géométrique, le score est plafonné : des correspondances éparses ne suffisent pas.
     */
    fun orbScore(goodMatches: Int, inliers: Int?, c: ClassicalConfig): Float {
        if (goodMatches < c.orbMinMatches) return 0f
        val match = ScoreMath.ramp(goodMatches.toFloat(), 0f, c.orbMatchFull.toFloat())
        val factor = when {
            inliers == null -> 0.5f
            inliers < c.orbMinInliers -> 0.2f
            else -> 0.4f + 0.6f * (inliers.toFloat() / goodMatches)
        }
        return ScoreMath.clamp01(match * factor)
    }
}
