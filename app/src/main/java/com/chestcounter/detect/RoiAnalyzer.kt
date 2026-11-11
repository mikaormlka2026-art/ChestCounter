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
 * Orchestre les méthodes du lot 1 sur une région candidate.
 * Entrée : frame RGBA complète (Mat) + boîte en coordonnées de la frame. Aucune copie de la frame :
 * la ROI est une vue (submat), puis normalisée dans des tampons réutilisés.
 * À créer après OpenCVLoader.initLocal().
 */
class RoiAnalyzer(private val cfg: ClassicalConfig = ClassicalConfig()) {
    private val smallRgba = Mat()
    private val smallGray = Mat()
    private val hsvAnalyzer = HsvAnalyzer(cfg)
    private val huShape = HuShapeAnalyzer()
    private val contourAnalyzer = ContourAnalyzer(cfg, huShape)
    private val refHu = HashMap<DetectionKind, DoubleArray>()

    /** Référence de silhouette (modèle coffre fermé / ouvert), mêmes traitements que les candidats. */
    @Synchronized
    fun setReference(kind: DetectionKind, rgba: Mat) {
        try {
            if (!prepare(rgba)) {
                refHu.remove(kind)
                return
            }
            val hu = contourAnalyzer.analyze(smallGray).hu
            if (hu != null) refHu[kind] = hu else refHu.remove(kind)
        } catch (t: Throwable) {
            refHu.remove(kind)
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
            RoiScores(
                hsvScore = hsv,
                edgeScore = c.edgeScore,
                contourScore = c.contourScore,
                geometryScore = c.geometryScore,
                shapeScore = shapeScore(c.hu, kind),
                processingUs = us()
            )
        } catch (t: Throwable) {
            RoiScores.none(us(), "Erreur ROI : ${t.message}")
        } finally {
            roi.release()
        }
    }

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

    @Synchronized
    fun release() {
        smallRgba.release()
        smallGray.release()
        hsvAnalyzer.release()
        contourAnalyzer.release()
        huShape.release()
        refHu.clear()
    }
}
