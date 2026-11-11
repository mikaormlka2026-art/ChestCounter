package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Analyse temporelle d'un coffre suivi : compare sa ROI à l'observation précédente.
 *
 * 1. la ROI est ramenée à une petite taille (niveaux de gris) ;
 * 2. le flux optique estime le déplacement d'ensemble (mouvement de caméra) ;
 * 3. les deux images sont recalées sur leur zone commune, puis comparées par différence d'images
 *    et par SSIM ; le mouvement local résiduel sert de motionScore.
 *
 * Si le déplacement est trop grand, ou si des points texturés n'ont pas pu être suivis,
 * le résultat est « non mesurable » (null) : mieux vaut aucune preuve qu'un faux changement.
 *
 * trackId = identifiant du coffre suivi (fourni par le tracker). À créer après OpenCVLoader.initLocal().
 */
class TemporalAnalyzer(private val cfg: ClassicalConfig = ClassicalConfig()) {
    private val history = TemporalHistory(cfg.temporalMaxGapMs)
    private val flow = OpticalFlowAnalyzer(cfg)
    private val smallRgba = Mat()
    private val currGray = Mat()
    private val prevGray = Mat()
    private var currBytes = ByteArray(0)
    private var alignPrev = ByteArray(0)
    private var alignCurr = ByteArray(0)

    @Synchronized
    fun analyze(frameRgba: Mat, box: BoundingBox, trackId: Int, nowMs: Long): TemporalScores {
        val t0 = System.nanoTime()
        if (!cfg.temporalEnabled) return TemporalScores.none(elapsedUs(t0))

        val fw = frameRgba.cols()
        val fh = frameRgba.rows()
        if (fw <= 0 || fh <= 0 || !box.isValid()) return TemporalScores.none(elapsedUs(t0), "ROI invalide")

        val x0 = floor(box.left).toInt().coerceIn(0, fw - 1)
        val y0 = floor(box.top).toInt().coerceIn(0, fh - 1)
        val x1 = ceil(box.right).toInt().coerceIn(x0, fw)
        val y1 = ceil(box.bottom).toInt().coerceIn(y0, fh)
        if (x1 - x0 < cfg.minRoiSidePx || y1 - y0 < cfg.minRoiSidePx) {
            return TemporalScores.none(elapsedUs(t0), "ROI trop petite")
        }

        val roi = frameRgba.submat(y0, y1, x0, x1)
        var stored = false
        var nw = 0
        var nh = 0
        try {
            val w = roi.cols()
            val h = roi.rows()
            val snap = history.get(trackId, nowMs)

            val scale = cfg.temporalRoiPx.toDouble() / max(w, h)
            nw = max(8, (w * scale).roundToInt())
            nh = max(8, (h * scale).roundToInt())

            // Même taille que l'observation précédente pour pouvoir comparer pixel à pixel ;
            // si les proportions ont trop changé, l'historique n'est pas comparable.
            var usable: RoiSnapshot? = snap
            if (snap != null) {
                val aNow = nw.toFloat() / nh
                val aOld = snap.width.toFloat() / snap.height
                if (abs(aNow - aOld) / aOld <= 0.25f) {
                    nw = snap.width
                    nh = snap.height
                } else {
                    usable = null
                }
            }

            ensureBuffers(nw * nh)
            val interp = if (nw < w || nh < h) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
            Imgproc.resize(roi, smallRgba, Size(nw.toDouble(), nh.toDouble()), 0.0, 0.0, interp)
            Imgproc.cvtColor(smallRgba, currGray, Imgproc.COLOR_RGBA2GRAY)
            currGray.get(0, 0, currBytes)
            stored = true

            if (usable == null) return TemporalScores.none(elapsedUs(t0))
            return compare(usable, nw, nh, nowMs, t0)
        } catch (t: Throwable) {
            return TemporalScores.none(elapsedUs(t0), "Erreur temporelle : ${t.message}")
        } finally {
            roi.release()
            // L'observation courante remplace la précédente, après son utilisation.
            if (stored) history.put(trackId, nowMs, nw, nh, currBytes)
        }
    }

    private fun compare(snap: RoiSnapshot, nw: Int, nh: Int, nowMs: Long, t0: Long): TemporalScores {
        val gap = nowMs - snap.tMs
        prevGray.create(nh, nw, CvType.CV_8UC1)
        prevGray.put(0, 0, snap.data)

        val fr = flow.analyze(prevGray, currGray)
        val q = fr.quality
        val stats = fr.stats

        // Des points texturés existaient mais n'ont pas pu être suivis : mouvement trop brusque
        // ou changement de scène. Les comparaisons d'images ne seraient pas fiables.
        if (stats == null && fr.candidates >= 3) {
            return TemporalScores(null, null, null, q, null, null, null, gap, elapsedUs(t0))
        }

        var dx = 0
        var dy = 0
        if (stats != null) {
            if (!TemporalMath.translationReliable(stats.shiftX, stats.shiftY, cfg)) {
                return TemporalScores(null, null, null, q, null, stats.shiftX, stats.shiftY, gap, elapsedUs(t0))
            }
            dx = stats.shiftX.roundToInt()
            dy = stats.shiftY.roundToInt()
        }

        val ov = TemporalMath.alignOverlap(snap.data, currBytes, nw, nh, dx, dy, alignPrev, alignCurr)
            ?: return TemporalScores(null, null, null, q, null, stats?.shiftX, stats?.shiftY, gap, elapsedUs(t0))

        val n = ov.width * ov.height
        val ratio = TemporalMath.changedRatio(alignPrev, alignCurr, n, cfg.diffPixelThreshold)
        val change = TemporalMath.changeScore(ratio, cfg)
        val ssim = Ssim.compute(alignPrev, alignCurr, ov.width, ov.height)
        val structural = ssim?.let { TemporalMath.structuralChangeScore(it, cfg) }
        val motion = stats?.let { TemporalMath.motionScore(it.residualP75, cfg) }

        return TemporalScores(
            changeScore = change,
            structuralChangeScore = structural,
            motionScore = motion,
            flowQuality = q,
            ssim = ssim,
            shiftX = stats?.shiftX,
            shiftY = stats?.shiftY,
            gapMs = gap,
            processingUs = elapsedUs(t0)
        )
    }

    private fun ensureBuffers(n: Int) {
        if (currBytes.size != n) currBytes = ByteArray(n)
        if (alignPrev.size < n) {
            alignPrev = ByteArray(n)
            alignCurr = ByteArray(n)
        }
    }

    private fun elapsedUs(t0: Long): Long = (System.nanoTime() - t0) / 1000L

    /** À appeler quand un coffre suivi est perdu ou oublié par le tracker. */
    fun forget(trackId: Int) {
        history.remove(trackId)
    }

    fun prune(nowMs: Long) {
        history.prune(nowMs)
    }

    fun clear() {
        history.clear()
    }

    @Synchronized
    fun release() {
        history.clear()
        flow.release()
        smallRgba.release()
        currGray.release()
        prevGray.release()
    }
}
