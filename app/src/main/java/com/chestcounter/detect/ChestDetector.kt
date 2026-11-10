package com.chestcounter.detect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.min
import kotlin.math.roundToInt

enum class TemplateKind(val fileName: String) {
    CLOSED("closed.png"),
    OPEN("open.png")
}

data class DetectionResult(
    val closedScore: Float,
    val openScore: Float,
    val processingMs: Long,
    val error: String? = null
)

/**
 * Détection par modèles (template matching OpenCV).
 * Les modèles "coffre fermé" et "coffre ouvert" sont calibrés par l'utilisateur
 * depuis une vraie capture. La recherche se fait à plusieurs échelles.
 * Le bitmap reçu doit être en ARGB_8888.
 */
class ChestDetector(
    private val targetWidth: Int = 480,
    private val scales: DoubleArray = doubleArrayOf(0.8, 1.0, 1.25)
) {
    private var ready = false
    private var closedGray: Mat? = null
    private var openGray: Mat? = null

    @Synchronized
    fun init(): Boolean {
        ready = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            false
        }
        return ready
    }

    @Synchronized
    fun hasTemplates(): Boolean = closedGray != null && openGray != null

    @Synchronized
    fun setTemplate(kind: TemplateKind, bitmap: Bitmap) {
        val gray = bitmapToGray(bitmap)
        when (kind) {
            TemplateKind.CLOSED -> {
                closedGray?.release()
                closedGray = gray
            }
            TemplateKind.OPEN -> {
                openGray?.release()
                openGray = gray
            }
        }
    }

    fun saveTemplate(dir: File, kind: TemplateKind, bitmap: Bitmap): Boolean {
        return try {
            dir.mkdirs()
            FileOutputStream(File(dir, kind.fileName)).use {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            setTemplate(kind, bitmap)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /** Charge les modèles enregistrés. Retourne true si les deux sont chargés. */
    fun loadTemplates(dir: File): Boolean {
        for (kind in TemplateKind.values()) {
            val f = File(dir, kind.fileName)
            if (!f.exists()) continue
            val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: continue
            val argb = if (bmp.config == Bitmap.Config.ARGB_8888) bmp
            else bmp.copy(Bitmap.Config.ARGB_8888, false)
            if (argb != null) setTemplate(kind, argb)
        }
        return hasTemplates()
    }

    @Synchronized
    fun detect(frame: Bitmap): DetectionResult {
        val start = System.nanoTime()
        fun elapsed() = (System.nanoTime() - start) / 1_000_000L

        if (!ready) return DetectionResult(0f, 0f, elapsed(), "OpenCV non initialisé")
        val c = closedGray
        val o = openGray
        if (c == null && o == null) {
            return DetectionResult(0f, 0f, elapsed(), "Aucun modèle calibré")
        }

        var rgba: Mat? = null
        var gray: Mat? = null
        var small: Mat? = null
        return try {
            rgba = Mat()
            Utils.bitmapToMat(frame, rgba)
            gray = Mat()
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val factor = min(1.0, targetWidth.toDouble() / gray.cols())
            small = if (factor < 1.0) {
                val m = Mat()
                Imgproc.resize(
                    gray, m,
                    Size(gray.cols() * factor, gray.rows() * factor),
                    0.0, 0.0, Imgproc.INTER_AREA
                )
                m
            } else gray

            val closed = if (c != null) bestScore(small, c, factor) else 0f
            val open = if (o != null) bestScore(small, o, factor) else 0f
            DetectionResult(closed, open, elapsed())
        } catch (t: Throwable) {
            DetectionResult(0f, 0f, elapsed(), "Erreur détection : ${t.message}")
        } finally {
            if (small != null && small !== gray) small.release()
            gray?.release()
            rgba?.release()
        }
    }

    @Synchronized
    fun release() {
        closedGray?.release()
        openGray?.release()
        closedGray = null
        openGray = null
    }

    private fun bitmapToGray(b: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(b, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return gray
    }

    private fun bestScore(frame: Mat, template: Mat, factor: Double): Float {
        var best = 0f
        for (s in scales) {
            val k = factor * s
            val w = (template.cols() * k).roundToInt()
            val h = (template.rows() * k).roundToInt()
            if (w < 8 || h < 8 || w > frame.cols() || h > frame.rows()) continue
            val t = Mat()
            val res = Mat()
            try {
                Imgproc.resize(
                    template, t, Size(w.toDouble(), h.toDouble()),
                    0.0, 0.0, Imgproc.INTER_AREA
                )
                Imgproc.matchTemplate(frame, t, res, Imgproc.TM_CCOEFF_NORMED)
                val v = Core.minMaxLoc(res).maxVal.toFloat()
                if (!v.isNaN() && v > best) best = v
            } finally {
                t.release()
                res.release()
            }
        }
        return best.coerceIn(0f, 1f)
    }
}
