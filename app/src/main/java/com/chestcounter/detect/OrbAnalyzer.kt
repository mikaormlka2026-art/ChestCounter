package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import org.opencv.calib3d.Calib3d
import org.opencv.core.Core
import org.opencv.core.DMatch
import org.opencv.core.KeyPoint
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.roundToInt

/** score null = indisponible (aucune référence). Un score 0 signifie : mesuré, aucune preuve. */
class OrbResult(
    val score: Float?,
    val matchCount: Int?,
    val geometricConsistency: Float?
) {
    companion object {
        val UNAVAILABLE = OrbResult(null, null, null)
    }
}

private class OrbRef(val points: Array<Point>, val desc: Mat) {
    fun release() {
        desc.release()
    }
}

/**
 * Correspondance ORB entre une ROI (niveaux de gris) et les références coffre fermé / ouvert.
 * Étapes : points caractéristiques, descripteurs, appariement par force brute (Hamming),
 * test du ratio de Lowe, une seule correspondance par point de référence, puis estimation
 * d'une transformation (rotation + échelle + translation) par RANSAC pour écarter les faux appariements.
 * À créer après OpenCVLoader.initLocal().
 */
class OrbAnalyzer(private val cfg: ClassicalConfig) {
    private val orb: ORB = ORB.create(
        cfg.orbFeatures, 1.2f, 4, cfg.orbEdgeThreshold, 0, 2,
        ORB.HARRIS_SCORE, cfg.orbPatchSize, cfg.orbFastThreshold
    )
    private val matcher: DescriptorMatcher =
        DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)
    private val resized = Mat()
    private val noMask = Mat()
    private val kp = MatOfKeyPoint()
    private val desc = Mat()
    private val refs = HashMap<DetectionKind, OrbRef>()

    @Synchronized
    fun hasReference(kind: DetectionKind): Boolean = refs.containsKey(kind)

    @Synchronized
    fun setReference(kind: DetectionKind, gray: Mat) {
        refs.remove(kind)?.release()
        if (!extract(gray)) return
        val pts: Array<Point> = kp.toArray().map { it.pt }.toTypedArray()
        refs[kind] = OrbRef(pts, desc.clone())
    }

    @Synchronized
    fun clearReference(kind: DetectionKind) {
        refs.remove(kind)?.release()
    }

    @Synchronized
    fun analyze(gray: Mat, kind: DetectionKind?): OrbResult {
        val candidates = if (kind != null) listOfNotNull(refs[kind]) else refs.values.toList()
        if (candidates.isEmpty()) return OrbResult.UNAVAILABLE
        if (!extract(gray)) return OrbResult(0f, 0, null)

        val query = kp.toArray()
        var best: OrbResult? = null
        for (r in candidates) {
            val res = matchAgainst(query, r)
            if (best == null || (res.score ?: 0f) > (best.score ?: 0f)) best = res
        }
        return best ?: OrbResult.UNAVAILABLE
    }

    private fun extract(gray: Mat): Boolean {
        val w = gray.cols()
        val h = gray.rows()
        if (w <= 0 || h <= 0) return false
        val scale = cfg.orbRoiSidePx.toDouble() / max(w, h)
        val nw = max(1, (w * scale).roundToInt())
        val nh = max(1, (h * scale).roundToInt())
        val interp = if (scale < 1.0) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
        Imgproc.resize(gray, resized, Size(nw.toDouble(), nh.toDouble()), 0.0, 0.0, interp)
        orb.detectAndCompute(resized, noMask, kp, desc)
        return !desc.empty() && desc.rows() >= 1
    }

    private fun matchAgainst(query: Array<KeyPoint>, ref: OrbRef): OrbResult {
        if (desc.rows() < 2 || ref.desc.rows() < 2) return OrbResult(0f, 0, null)
        val knn = ArrayList<MatOfDMatch>()
        try {
            matcher.knnMatch(desc, ref.desc, knn, 2)

            // Une seule correspondance par point de référence : évite le « plusieurs pour un »
            // typique des textures répétitives.
            val bestByTrain = HashMap<Int, DMatch>()
            for (pair in knn) {
                val m = pair.toArray()
                if (m.size < 2) continue
                if (!FeatureMath.passesRatioTest(m[0].distance, m[1].distance, cfg.orbRatio)) continue
                val cur = bestByTrain[m[0].trainIdx]
                if (cur == null || m[0].distance < cur.distance) bestByTrain[m[0].trainIdx] = m[0]
            }
            val good = bestByTrain.values.toList()
            val count = good.size
            if (count < cfg.orbMinMatches) return OrbResult(0f, count, null)

            val inliers = countInliers(good, query, ref)
            return OrbResult(
                FeatureMath.orbScore(count, inliers, cfg),
                count,
                FeatureMath.geometricConsistency(count, inliers)
            )
        } finally {
            knn.forEach { it.release() }
        }
    }

    /** Nombre de correspondances cohérentes avec une même transformation, null si non estimable. */
    private fun countInliers(good: List<DMatch>, query: Array<KeyPoint>, ref: OrbRef): Int? {
        if (good.size < 3) return null
        val src = MatOfPoint2f(*Array<Point>(good.size) { ref.points[good[it].trainIdx] })
        val dst = MatOfPoint2f(*Array<Point>(good.size) { query[good[it].queryIdx].pt })
        val inl = Mat()
        var m: Mat? = null
        try {
            m = Calib3d.estimateAffinePartial2D(src, dst, inl, Calib3d.RANSAC, cfg.orbRansacThresholdPx)
            return if (m.empty()) null else Core.countNonZero(inl)
        } catch (t: Throwable) {
            return null
        } finally {
            src.release()
            dst.release()
            inl.release()
            m?.release()
        }
    }

    @Synchronized
    fun release() {
        resized.release()
        noMask.release()
        kp.release()
        desc.release()
        refs.values.forEach { it.release() }
        refs.clear()
    }
}
