package com.chestcounter.detect

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.chestcounter.config.ClassicalConfig
import com.chestcounter.config.DetectionConfig
import com.chestcounter.config.FusionMode
import com.chestcounter.config.PipelineConfig
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.Mat
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class TemplateKind(val fileName: String, val detectionKind: DetectionKind) {
    CLOSED("closed.png", DetectionKind.CLOSED),
    OPEN("open.png", DetectionKind.OPEN)
}

private class ScaledTemplates(val factor: Double, val mats: List<Mat>) {
    fun release() {
        mats.forEach { it.release() }
    }
}

private class MatchOutcome(val best: Float, val detections: List<Detection>)

private class AnalysisBatch(
    val kept: List<Detection>,
    val vetoed: List<Detection>,
    val micros: Long
)

class ChestDetector(
    private val targetWidth: Int = 480,
    private val scales: DoubleArray = doubleArrayOf(0.8, 1.0, 1.25),
    private val listThreshold: Float = 0.5f,
    private val maxPerKind: Int = 8,
    private val pipeline: PipelineConfig = PipelineConfig(),
    private val classical: ClassicalConfig = ClassicalConfig(),
    detectionConfig: DetectionConfig = DetectionConfig()
) {
    private var ready = false
    private var closedGray: Mat? = null
    private var openGray: Mat? = null
    private var closedScaled: ScaledTemplates? = null
    private var openScaled: ScaledTemplates? = null
    private var rgbaBuf: Mat? = null
    private var grayBuf: Mat? = null
    private var smallBuf: Mat? = null
    private var roiAnalyzer: RoiAnalyzer? = null
    private var temporal: TemporalAnalyzer? = null
    private val fusionEngine = EvidenceFusionEngine(detectionConfig)

    @Synchronized
    fun init(): Boolean {
        ready = try {
            OpenCVLoader.initLocal()
        } catch (t: Throwable) {
            false
        }
        if (ready && rgbaBuf == null) {
            rgbaBuf = Mat()
            grayBuf = Mat()
            smallBuf = Mat()
        }
        if (ready && roiAnalyzer == null) {
            roiAnalyzer = RoiAnalyzer(classical)
            temporal = TemporalAnalyzer(classical)
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
                closedScaled?.release()
                closedScaled = null
            }
            TemplateKind.OPEN -> {
                openGray?.release()
                openGray = gray
                openScaled?.release()
                openScaled = null
            }
        }
        // Références des méthodes classiques (Hu, HOG, ORB) : le même modèle que le template matching.
        try {
            roiAnalyzer?.setReference(kind.detectionKind, bitmap)
        } catch (t: Throwable) {
            // les méthodes sans référence restent simplement indisponibles
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
        val rgba = rgbaBuf
        val gray = grayBuf
        val small = smallBuf
        if (rgba == null || gray == null || small == null) {
            return DetectionResult(0f, 0f, elapsed(), "Tampons OpenCV absents")
        }

        return try {
            Utils.bitmapToMat(frame, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)

            val factor = min(1.0, targetWidth.toDouble() / gray.cols())
            val src: Mat = if (factor < 1.0) {
                Imgproc.resize(
                    gray, small,
                    Size(gray.cols() * factor, gray.rows() * factor),
                    0.0, 0.0, Imgproc.INTER_AREA
                )
                small
            } else gray

            val all = ArrayList<Detection>()
            var closedBest = 0f
            var openBest = 0f

            if (c != null) {
                val r = match(
                    src,
                    ensureScaled(TemplateKind.CLOSED, c, factor),
                    factor,
                    DetectionKind.CLOSED
                )
                closedBest = r.best
                all.addAll(r.detections)
            }
            if (o != null) {
                val r = match(
                    src,
                    ensureScaled(TemplateKind.OPEN, o, factor),
                    factor,
                    DetectionKind.OPEN
                )
                openBest = r.best
                all.addAll(r.detections)
            }

            val batch = analyzeCandidates(rgba, all)

            DetectionResult(
                closedBest, openBest, elapsed(), null,
                batch.kept, frame.width, frame.height,
                vetoed = batch.vetoed, analysisUs = batch.micros
            )
        } catch (t: Throwable) {
            DetectionResult(0f, 0f, elapsed(), "Erreur détection : ${t.message}")
        }
    }

    /**
     * Analyse temporelle d'un coffre SUIVI sur la dernière image traitée par detect().
     * À appeler juste après detect(), sur le même fil. null si indisponible.
     */
    @Synchronized
    fun analyzeTemporal(trackId: Int, box: BoundingBox, nowMs: Long): TemporalScores? {
        if (!ready || !pipeline.temporalEnabled) return null
        val t = temporal ?: return null
        val rgba = rgbaBuf ?: return null
        if (rgba.empty()) return null
        return t.analyze(rgba, box, trackId, nowMs)
    }

    @Synchronized
    fun forgetTrack(trackId: Int) {
        temporal?.forget(trackId)
    }

    @Synchronized
    fun release() {
        closedGray?.release()
        openGray?.release()
        closedScaled?.release()
        openScaled?.release()
        rgbaBuf?.release()
        grayBuf?.release()
        smallBuf?.release()
        roiAnalyzer?.release()
        temporal?.release()
        closedGray = null
        openGray = null
        closedScaled = null
        openScaled = null
        rgbaBuf = null
        grayBuf = null
        smallBuf = null
        roiAnalyzer = null
        temporal = null
        ready = false
    }

    /**
     * Analyse classique des meilleurs candidats (HSV, contours, Hu, HOG, ORB), puis fusion des preuves.
     * Cadence adaptative : seuls les candidats assez forts sont analysés, en nombre limité et dans un budget de temps.
     * En mode VETO, un candidat contredit par plusieurs canaux indépendants est retiré de la liste.
     */
    private fun analyzeCandidates(rgba: Mat, all: List<Detection>): AnalysisBatch {
        val analyzer = roiAnalyzer
        if (analyzer == null || pipeline.fusionMode == FusionMode.OFF || all.isEmpty()) {
            return AnalysisBatch(all, emptyList(), 0L)
        }
        val t0 = System.nanoTime()
        analyzer.beginFrame()

        val order = all.indices
            .filter { all[it].score >= pipeline.analyzeMinScore }
            .sortedByDescending { all[it].score }
            .take(pipeline.maxAnalyzedPerFrame)

        val updated = ArrayList<Detection>(all)
        val vetoedIdx = HashSet<Int>()
        for (idx in order) {
            if ((System.nanoTime() - t0) / 1_000_000L > pipeline.analysisBudgetMs) break
            val d = all[idx]
            val roi = analyzer.analyze(rgba, d.box, d.kind)
            val outcome = fusionEngine.evaluate(CandidateEvidence(d.score, roi))
            val veto = pipeline.fusionMode == FusionMode.VETO &&
                outcome.status == CandidateStatus.REJECTED &&
                outcome.reasons.any { it.contains(VETO_MARK) }
            updated[idx] = d.copy(analysis = CandidateAnalysis(roi, outcome, veto))
            if (veto) vetoedIdx.add(idx)
        }

        val kept = ArrayList<Detection>(updated.size)
        val dropped = ArrayList<Detection>()
        for (i in updated.indices) {
            if (i in vetoedIdx) dropped.add(updated[i]) else kept.add(updated[i])
        }
        return AnalysisBatch(kept, dropped, (System.nanoTime() - t0) / 1000L)
    }

    private fun bitmapToGray(b: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(b, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        return gray
    }

    private fun ensureScaled(kind: TemplateKind, template: Mat, factor: Double): ScaledTemplates {
        val current = if (kind == TemplateKind.CLOSED) closedScaled else openScaled
        if (current != null && current.factor == factor) return current
        current?.release()
        val built = buildScaled(template, factor)
        if (kind == TemplateKind.CLOSED) closedScaled = built else openScaled = built
        return built
    }

    private fun buildScaled(template: Mat, factor: Double): ScaledTemplates {
        val mats = ArrayList<Mat>()
        for (s in scales) {
            val k = factor * s
            val w = (template.cols() * k).roundToInt()
            val h = (template.rows() * k).roundToInt()
            if (w < 8 || h < 8) continue
            val t = Mat()
            Imgproc.resize(
                template, t, Size(w.toDouble(), h.toDouble()),
                0.0, 0.0, Imgproc.INTER_AREA
            )
            mats.add(t)
        }
        return ScaledTemplates(factor, mats)
    }

    private fun match(
        src: Mat,
        scaled: ScaledTemplates,
        factor: Double,
        kind: DetectionKind
    ): MatchOutcome {
        var best = 0f
        val candidates = ArrayList<Detection>()
        for (t in scaled.mats) {
            if (t.cols() > src.cols() || t.rows() > src.rows()) continue
            val res = Mat()
            try {
                Imgproc.matchTemplate(src, t, res, Imgproc.TM_CCOEFF_NORMED)
                val top = extractPeaks(res, t.cols(), t.rows(), factor, kind, candidates)
                if (top > best) best = top
            } finally {
                res.release()
            }
        }
        return MatchOutcome(
            best.coerceIn(0f, 1f),
            nonMaxSuppression(candidates, 0.4f, maxPerKind)
        )
    }

    private fun extractPeaks(
        res: Mat,
        tw: Int,
        th: Int,
        factor: Double,
        kind: DetectionKind,
        out: MutableList<Detection>
    ): Float {
        var first = 0f
        var n = 0
        while (n < maxPerKind) {
            val mm = Core.minMaxLoc(res)
            val v = mm.maxVal.toFloat()
            if (v.isNaN()) break
            if (n == 0) first = v
            if (v < listThreshold) break

            val x = mm.maxLoc.x
            val y = mm.maxLoc.y
            out.add(
                Detection(
                    kind,
                    BoundingBox.fromSize(
                        (x / factor).toFloat(),
                        (y / factor).toFloat(),
                        (tw / factor).toFloat(),
                        (th / factor).toFloat()
                    ),
                    v.coerceIn(0f, 1f)
                )
            )

            val x0 = max(0, (x - tw / 2).toInt())
            val y0 = max(0, (y - th / 2).toInt())
            val x1 = min(res.cols(), (x + tw / 2).toInt() + 1)
            val y1 = min(res.rows(), (y + th / 2).toInt() + 1)
            if (x1 <= x0 || y1 <= y0) break
            val sub = res.submat(y0, y1, x0, x1)
            sub.setTo(Scalar(-1.0))
            sub.release()
            n++
        }
        return first
    }

    private companion object {
        /** Mot présent dans la raison de rejet par contradiction (voir EvidenceFusionEngine). */
        const val VETO_MARK = "contredisent"
    }
}
