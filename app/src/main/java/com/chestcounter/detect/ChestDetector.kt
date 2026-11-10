package com.chestcounter.detect

import android.content.Context
import android.graphics.Bitmap
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgcodecs.Imgcodecs
import org.opencv.imgproc.Imgproc
import java.io.File

class ChestDetector(private val context: Context) {

    companion object {
        private const val SCALE = 0.5
        private const val FILE_CLOSED = "tpl_closed.png"
        private const val FILE_OPEN = "tpl_open.png"
    }

    /** Seuil de ressemblance (0 à 1). Plus haut = plus strict. */
    var threshold = 0.80

    private var closedTpl: Mat? = null
    private var openTpl: Mat? = null

    val ready: Boolean get() = closedTpl != null && openTpl != null

    fun init(): Boolean {
        val ok = OpenCVLoader.initLocal()
        if (ok) loadTemplates()
        return ok
    }

    private fun file(name: String) = File(context.filesDir, name)

    private fun loadTemplates() {
        closedTpl = load(FILE_CLOSED)
        openTpl = load(FILE_OPEN)
    }

    private fun load(name: String): Mat? {
        val f = file(name)
        if (!f.exists()) return null
        val m = Imgcodecs.imread(f.absolutePath, Imgcodecs.IMREAD_GRAYSCALE)
        return if (m.empty()) null else m
    }

    private fun toGraySmall(bmp: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(bmp, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        val small = Mat()
        Imgproc.resize(gray, small, Size(), SCALE, SCALE, Imgproc.INTER_AREA)
        rgba.release()
        gray.release()
        return small
    }

    /** Calibration : découpe la zone (x, y, w, h en pixels de l'écran) et l'enregistre. */
    fun saveTemplate(frame: Bitmap, x: Int, y: Int, w: Int, h: Int, isOpen: Boolean): Boolean {
        val small = toGraySmall(frame)
        val rx = (x * SCALE).toInt().coerceIn(0, small.cols() - 1)
        val ry = (y * SCALE).toInt().coerceIn(0, small.rows() - 1)
        val rw = (w * SCALE).toInt().coerceIn(1, small.cols() - rx)
        val rh = (h * SCALE).toInt().coerceIn(1, small.rows() - ry)
        val crop = Mat(small, Rect(rx, ry, rw, rh))
        val name = if (isOpen) FILE_OPEN else FILE_CLOSED
        val ok = Imgcodecs.imwrite(file(name).absolutePath, crop)
        small.release()
        loadTemplates()
        return ok
    }

    fun clearTemplates() {
        file(FILE_CLOSED).delete()
        file(FILE_OPEN).delete()
        closedTpl = null
        openTpl = null
    }

    private fun score(img: Mat, tpl: Mat): Double {
        if (tpl.cols() > img.cols() || tpl.rows() > img.rows()) return 0.0
        val res = Mat()
        Imgproc.matchTemplate(img, tpl, res, Imgproc.TM_CCOEFF_NORMED)
        val v = Core.minMaxLoc(res).maxVal
        res.release()
        return if (v.isNaN() || v.isInfinite()) 0.0 else v
    }

    fun analyze(frame: Bitmap): Observation {
        val closed = closedTpl ?: return Observation.NONE
        val open = openTpl ?: return Observation.NONE
        val img = toGraySmall(frame)
        val sClosed = score(img, closed)
        val sOpen = score(img, open)
        img.release()
        return when {
            sOpen >= threshold && sOpen > sClosed -> Observation.OPEN
            sClosed >= threshold -> Observation.CLOSED
            else -> Observation.NONE
        }
    }
}
