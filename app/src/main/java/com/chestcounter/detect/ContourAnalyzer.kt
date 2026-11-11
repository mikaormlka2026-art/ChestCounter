package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.MatOfInt
import org.opencv.core.MatOfPoint
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

class ContourResult(
    val edgeScore: Float,
    val contourScore: Float,
    val geometryScore: Float,
    /** Moments de Hu du contour principal, null si aucune structure exploitable. */
    val hu: DoubleArray?
)

/** Canny + contours + géométrie sur une ROI en niveaux de gris déjà normalisée. */
class ContourAnalyzer(
    private val cfg: ClassicalConfig,
    private val huShape: HuShapeAnalyzer
) {
    private val blurred = Mat()
    private val edges = Mat()
    private val closed = Mat()
    private val hierarchy = Mat()
    private val blurSize: Size
    private val kernel: Mat

    init {
        val b = max(1, if (cfg.blurKernel % 2 == 0) cfg.blurKernel + 1 else cfg.blurKernel)
        blurSize = Size(b.toDouble(), b.toDouble())
        val k = max(1, cfg.closeKernel)
        kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(k.toDouble(), k.toDouble()))
    }

    fun analyze(gray: Mat): ContourResult {
        val area = gray.cols().toFloat() * gray.rows().toFloat()
        if (area <= 0f) return ContourResult(0f, 0f, 0f, null)

        Imgproc.GaussianBlur(gray, blurred, blurSize, 0.0)
        Imgproc.Canny(blurred, edges, cfg.cannyLow, cfg.cannyHigh)
        val density = Core.countNonZero(edges) / area
        val edgeScore = ScoreMath.edgeScore(density, cfg)

        Imgproc.morphologyEx(edges, closed, Imgproc.MORPH_CLOSE, kernel)
        val contours = ArrayList<MatOfPoint>()
        try {
            Imgproc.findContours(
                closed, contours, hierarchy,
                Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE
            )
            val minArea = cfg.minContourAreaFrac * area
            var main: MatOfPoint? = null
            var mainArea = 0.0
            var kept = 0
            var fragments = 0
            for (c in contours) {
                val a = Imgproc.contourArea(c)
                if (a >= minArea) {
                    kept++
                    if (a > mainArea) {
                        mainArea = a
                        main = c
                    }
                } else if (a >= cfg.minFragmentArea) {
                    fragments++
                }
            }
            val m = main ?: return ContourResult(edgeScore, 0f, 0f, null)

            val coverage = (mainArea / area).toFloat()
            val contourScore = ScoreMath.contourScore(coverage, ScoreMath.continuity(kept, fragments), cfg)

            val rect = Imgproc.boundingRect(m)
            val rectArea = rect.width.toDouble() * rect.height
            val aspect = if (rect.height > 0) rect.width.toFloat() / rect.height else 0f
            val extent = if (rectArea > 0.0) (mainArea / rectArea).toFloat() else 0f
            val geometry = ScoreMath.geometryScore(aspect, extent, solidity(m, mainArea), cfg)

            return ContourResult(edgeScore, contourScore, geometry, huShape.compute(m))
        } finally {
            contours.forEach { it.release() }
        }
    }

    private fun solidity(contour: MatOfPoint, area: Double): Float {
        val hullIdx = MatOfInt()
        try {
            Imgproc.convexHull(contour, hullIdx)
            val idx = hullIdx.toArray()
            if (idx.size < 3) return 0f
            val pts = contour.toArray()
            val hull = MatOfPoint(*Array<Point>(idx.size) { pts[idx[it]] })
            try {
                val hullArea = Imgproc.contourArea(hull)
                return if (hullArea > 0.0) (area / hullArea).toFloat().coerceIn(0f, 1f) else 0f
            } finally {
                hull.release()
            }
        } finally {
            hullIdx.release()
        }
    }

    fun release() {
        blurred.release()
        edges.release()
        closed.release()
        hierarchy.release()
        kernel.release()
    }
}
