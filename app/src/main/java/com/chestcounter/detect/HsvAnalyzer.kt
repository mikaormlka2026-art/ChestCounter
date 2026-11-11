package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.imgproc.Imgproc
import kotlin.math.max
import kotlin.math.min

/** Analyse HSV d'une ROI RGBA déjà normalisée. Preuve secondaire : jamais suffisante seule. */
class HsvAnalyzer(private val cfg: ClassicalConfig) {
    private val rgb = Mat()
    private val hsv = Mat()
    private val mask = Mat()
    private val dark = Mat()

    fun analyze(smallRgba: Mat): Float {
        val w = smallRgba.cols()
        val h = smallRgba.rows()
        val area = w * h
        if (area <= 0) return 0f

        Imgproc.cvtColor(smallRgba, rgb, Imgproc.COLOR_RGBA2RGB)
        Imgproc.cvtColor(rgb, hsv, Imgproc.COLOR_RGB2HSV)

        // Tolérance à la luminosité : le seuil de V baisse si la ROI est globalement sombre.
        val meanV = Core.mean(hsv).`val`[2]
        val effV = min(cfg.hsvValMin.toDouble(), max(40.0, 0.6 * meanV))

        Core.inRange(
            hsv,
            Scalar(cfg.hsvHueMin.toDouble(), cfg.hsvSatMin.toDouble(), effV),
            Scalar(cfg.hsvHueMax.toDouble(), 255.0, 255.0),
            mask
        )
        val ratio = Core.countNonZero(mask).toFloat() / area

        var covered = 0
        for (gy in 0 until 3) {
            for (gx in 0 until 3) {
                val r0 = gy * h / 3
                val r1 = (gy + 1) * h / 3
                val c0 = gx * w / 3
                val c1 = (gx + 1) * w / 3
                if (r1 <= r0 || c1 <= c0) continue
                val cell = mask.submat(r0, r1, c0, c1)
                val cellArea = (r1 - r0) * (c1 - c0)
                if (Core.countNonZero(cell).toFloat() / cellArea >= cfg.hsvCellRatioMin) covered++
                cell.release()
            }
        }

        Core.inRange(
            hsv,
            Scalar(0.0, 0.0, 0.0),
            Scalar(179.0, 255.0, cfg.hsvDarkValMax.toDouble()),
            dark
        )
        val darkRatio = Core.countNonZero(dark).toFloat() / area

        return ScoreMath.hsvScore(ratio, covered, 9, darkRatio, cfg)
    }

    fun release() {
        rgb.release()
        hsv.release()
        mask.release()
        dark.release()
    }
}
