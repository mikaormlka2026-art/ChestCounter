package com.chestcounter.detect

import android.graphics.Bitmap
import com.chestcounter.config.ClassicalConfig
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Orchestre les méthodes classiques sur une région candidate : HSV, Canny/contours/géométrie,
 * Hu, HOG et ORB.
 * Entrée : frame RGBA complète (Mat) + boîte en coordonnées de la frame. Aucune copie de la frame :
 * la ROI est une vue (submat), puis normalisée dans des tampons réutilisés.
 * À créer après OpenCVLoader.initLocal().
 * Appeler beginFrame() une fois par image si hogEveryNFrames / orbEveryNFrames sont > 1.
 */
class RoiAnalyzer(private val cfg: ClassicalConfig = ClassicalConfig()) {
    private val smallRgba = Mat()
    private val smallGray = Mat()
    private val roiGray = Mat()
    private val hsvAnalyzer = HsvAnalyzer(cfg)
    private val huShape = HuShapeAnalyzer()
    private val contourAnalyzer = ContourAnalyzer(cfg, huShape)
    private val hogAnalyzer = HogAnalyzer(cfg)
    private val orbAnalyzer = OrbAnalyzer(cfg)
    private val refHu = HashMap<DetectionKind, DoubleArray>()
    private val refHog = HashMap<DetectionKind, FloatArray>()
    private var frameIndex = 0L

    /** À appeler une fois par image analysée (cadence adaptative de HOG et ORB). */
    @Synchronized
    fun beginFrame() {
        frameIndex++
    }

    /** Références (modèle coffre fermé / ouvert) : mêmes traitements que les candidats. */
    @Synchronized
    fun setReference(kind: DetectionKind, rgba: Mat) {
        refHu.remove(kind)
        refHog.remove(kind)
        orbAnalyzer.clearReference(kind)
        try {
            if (prepare(rgba)) {
                val hu = contourAnalyzer.analyze(smallGray).hu
                if (hu != null) refHu[kind] = hu
                val hogDesc = hogAnalyzer.compute(smallGray)
                if (hogDesc != null) refHog[kind] = hogDesc.copyOf()
            }
        } catch (t: Throwable) {
            // référence partielle : les méthodes sans référence restent indisponibles
        }
        try {
            if (rgba.cols() > 0 && rgba.rows() > 0) {
                Imgproc.cvtColor(rgba, roiGray, Imgproc.COLOR_RGBA2GRAY)
                orbAnalyzer.setReference(kind, roiGray)
            }
        } catch (t: Throwable) {
            // idem
        }
    }

    @Synchronized
    fun setReference(kind: DetectionKind, bitmap: Bitmap) {
        val rgba = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            setReference(kind, rgba)
        } finally {
            rgba.release()
        }
    }

    @Synchronized
    fun clearReference(kind: DetectionKind) {
        refHu.remove(kind)
        refHog.remove(kind)
        orbAnalyzer.clearReference(kind)
    }

    @Synchronized
    fun analyze(frameRgba: Mat, box: BoundingBox, kind: DetectionKind? = null): RoiScores {
        val t0 = System.nanoTime()
        fun us() = (System.nanoTime() - t0) / 1000L

        val fw = frameRgba.cols()
        val fh = frameRgba.rows()
        if (fw <= 0 || fh <= 0 || !box.isValid()) return RoiScores.none(us(), "ROI invalide")

        val x0 = floor(box.left).toInt().coerceIn(0, fw - 1)
        val y0 = floor(box.top).toInt().coerceIn(0, fh - 1)
        val x1 = ceil(box.right).toInt().coerceIn(x0, fw)
        val y1 = ceil(box.bottom).toInt().coerceIn(y0, fh)
        if (x1 - x0 < cfg.minRoiSidePx || y1 - y0 < cfg.minRoiSidePx) {
            return RoiScores.none(us(), "ROI trop petite")
        }

        val roi = frameRgba.submat(y0, y1, x0, x1)
        return try {
            if (!prepare(roi)) return RoiScores.none(us(), "ROI vide")
            val hsv = hsvAnalyzer.analyze(smallRgba)
            val c = contourAnalyzer.analyze(smallGray)
            val hog = if (due(cfg.hogEnabled, cfg.hogEveryNFrames)) hogScore(kind) else null
            val orb = if (due(cfg.orbEnabled, cfg.orbEveryNFrames)) orbResult(roi, kind) else OrbResult.UNAVAILABLE
            RoiScores(
                hsvScore = hsv,
                edgeScore = c.edgeScore,
                contourScore = c.contourScore,
                geometryScore = c.geometryScore,
                shapeScore = shapeScore(c.hu, kind),
                processingUs = us(),
                hogScore = hog,
                orbScore = orb.score,
                orbMatchCount = orb.matchCount,
                geometricConsistency = orb.geometricConsistency
            )
        } catch (t: Throwable) {
            RoiScores.none(us(), "Erreur ROI : ${t.message}")
        } finally {
            roi.release()
        }
    }

    private fun due(enabled: Boolean, everyN: Int): Boolean =
        enabled && frameIndex % max(1, everyN).toLong() == 0L

    private fun prepare(src: Mat): Boolean {
        val w = src.cols()
        val h = src.rows()
        if (w <= 0 || h <= 0) return false
        val scale = cfg.roiSidePx.toDouble() / max(w, h)
        val nw = max(1, (w * scale).roundToInt())
        val nh = max(1, (h * scale).roundToInt())
        val interp = if (scale < 1.0) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
        Imgproc.resize(src, smallRgba, Size(nw.toDouble(), nh.toDouble()), 0.0, 0.0, interp)
        Imgproc.cvtColor(smallRgba, smallGray, Imgproc.COLOR_RGBA2GRAY)
        return true
    }

    /** null si aucune silhouette ou aucune référence : c'est contourScore qui porte l'absence de structure. */
    private fun shapeScore(hu: DoubleArray?, kind: DetectionKind?): Float? {
        if (hu == null) return null
        val refs = if (kind != null) listOfNotNull(refHu[kind]) else refHu.values.toList()
        var best: Float? = null
        for (r in refs) {
            val s = HuMath.similarity(hu, r, cfg.huScale) ?: continue
            if (best == null || s > best) best = s
        }
        return best
    }

    /** HOG sur la ROI déjà normalisée (smallGray) ; null si pas de référence ou erreur. */
    private fun hogScore(kind: DetectionKind?): Float? {
        val refs = if (kind != null) listOfNotNull(refHog[kind]) else refHog.values.toList()
        if (refs.isEmpty()) return null
        return try {
            val d = hogAnalyzer.compute(smallGray) ?: return null
            var bestCos: Float? = null
            for (r in refs) {
                val cs = FeatureMath.cosine(d, r) ?: continue
                if (bestCos == null || cs > bestCos) bestCos = cs
            }
            bestCos?.let { FeatureMath.hogScore(it, cfg) }
        } catch (t: Throwable) {
            null
        }
    }

    /** ORB sur la ROI en pleine résolution (plus de détails que la ROI réduite). */
    private fun orbResult(roi: Mat, kind: DetectionKind?): OrbResult {
        return try {
            Imgproc.cvtColor(roi, roiGray, Imgproc.COLOR_RGBA2GRAY)
            orbAnalyzer.analyze(roiGray, kind)
        } catch (t: Throwable) {
            OrbResult.UNAVAILABLE
        }
    }

    @Synchronized
    fun release() {
        smallRgba.release()
        smallGray.release()
        roiGray.release()
        hsvAnalyzer.release()
        contourAnalyzer.release()
        huShape.release()
        hogAnalyzer.release()
        orbAnalyzer.release()
        refHu.clear()
        refHog.clear()
    }
}
