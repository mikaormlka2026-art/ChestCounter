package com.chestcounter.detect

import com.chestcounter.config.ClassicalConfig
import org.opencv.core.Mat
import org.opencv.core.MatOfFloat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.HOGDescriptor
import kotlin.math.max

/**
 * Descripteur HOG d'une ROI en niveaux de gris (8 bits, 1 canal).
 * La ROI est mise à win x win avant extraction ; blocs 16x16, pas 8, cellules 8x8, 9 orientations.
 * Pas de classifieur : la comparaison se fait par similarité cosinus avec des références (FeatureMath).
 * À créer après OpenCVLoader.initLocal().
 */
class HogAnalyzer(cfg: ClassicalConfig) {
    private val win = max(16, (cfg.hogWinPx / 8) * 8)
    private val hog = HOGDescriptor(
        Size(win.toDouble(), win.toDouble()),
        Size(16.0, 16.0),
        Size(8.0, 8.0),
        Size(8.0, 8.0),
        9
    )
    private val resized = Mat()
    private val desc = MatOfFloat()
    private var buf = FloatArray(0)

    /**
     * Retourne le descripteur dans un tableau RÉUTILISÉ à chaque appel :
     * à copier (copyOf) si on veut le conserver, par exemple pour une référence.
     */
    fun compute(gray: Mat): FloatArray? {
        if (gray.cols() <= 0 || gray.rows() <= 0) return null
        val interp = if (gray.cols() > win || gray.rows() > win) Imgproc.INTER_AREA else Imgproc.INTER_LINEAR
        Imgproc.resize(gray, resized, Size(win.toDouble(), win.toDouble()), 0.0, 0.0, interp)
        hog.compute(resized, desc)
        val n = desc.total().toInt()
        if (n <= 0) return null
        if (buf.size != n) buf = FloatArray(n)
        desc.get(0, 0, buf)
        return buf
    }

    fun release() {
        resized.release()
        desc.release()
    }
}
