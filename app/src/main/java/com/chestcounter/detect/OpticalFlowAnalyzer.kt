package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import org.opencv.core.Mat
import org.opencv.core.MatOfByte
import org.opencv.core.MatOfFloat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.video.Video
import kotlin.math.max
import kotlin.math.min

/**
 * stats = null : flux non mesurable (trop peu de points bien suivis, ROI sans texture, erreur).
 * quality reste renseignée pour le diagnostic.
 */
class FlowResult(
    val stats: FlowStats?,
    val tracked: Int,
    val candidates: Int,
    val quality: Float
) {
    companion object {
        val UNAVAILABLE = FlowResult(null, 0, 0, 0f)
    }
}

/**
 * Flux optique de Lucas-Kanade (pyramidal) entre deux ROI en niveaux de gris (8 bits, 1 canal,
 * même taille). Ce n'est PAS un détecteur de coffre : il mesure le mouvement d'ensemble de la ROI
 * (caméra) et le mouvement local résiduel, pour aider le suivi et la comparaison d'images.
 * Les tampons OpenCV sont réutilisés ; seules de petites listes de points (30 au plus) sont allouées.
 * À créer après OpenCVLoader.initLocal().
 */
class OpticalFlowAnalyzer(private val cfg: ClassicalConfig) {
    private val corners = MatOfPoint()
    private val prevPts = MatOfPoint2f()
    private val nextPts = MatOfPoint2f()
    private val status = MatOfByte()
    private val err = MatOfFloat()
    private val winSize: Size
    private val dx: FloatArray
    private val dy: FloatArray

    init {
        val win = max(5, cfg.flowWinPx).toDouble()
        winSize = Size(win, win)
        val cap = max(1, cfg.flowMaxCorners)
        dx = FloatArray(cap)
        dy = FloatArray(cap)
    }

    fun analyze(prevGray: Mat, currGray: Mat): FlowResult {
        if (prevGray.empty() || currGray.empty()) return FlowResult.UNAVAILABLE
        if (prevGray.cols() != currGray.cols() || prevGray.rows() != currGray.rows()) {
            return FlowResult.UNAVAILABLE
        }
        return try {
            Imgproc.goodFeaturesToTrack(
                prevGray, corners, cfg.flowMaxCorners, cfg.flowQualityLevel, cfg.flowMinDistancePx
            )
            val pts = corners.toArray()
            val candidates = pts.size
            if (candidates < 3) return FlowResult(null, 0, candidates, 0f)
            prevPts.fromArray(*pts)

            Video.calcOpticalFlowPyrLK(
                prevGray, currGray, prevPts, nextPts, status, err, winSize, cfg.flowPyrLevels
            )
            val p0 = prevPts.toArray()
            val p1 = nextPts.toArray()
            val st = status.toArray()

            var n = 0
            val limit = min(min(p0.size, p1.size), st.size)
            for (i in 0 until limit) {
                if (st[i].toInt() == 0) continue
                if (n >= dx.size) break
                dx[n] = (p1[i].x - p0[i].x).toFloat()
                dy[n] = (p1[i].y - p0[i].y).toFloat()
                n++
            }
            val quality = TemporalMath.flowQuality(n, candidates, cfg)
            if (n < cfg.flowMinTracked) return FlowResult(null, n, candidates, quality)
            FlowResult(TemporalMath.summarize(dx, dy, n), n, candidates, quality)
        } catch (t: Throwable) {
            FlowResult.UNAVAILABLE
        }
    }

    fun release() {
        corners.release()
        prevPts.release()
        nextPts.release()
        status.release()
        err.release()
    }
}
