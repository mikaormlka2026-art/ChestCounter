package com.chestcounter.diagnostic

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import com.chestcounter.detect.FusionOutcome
import com.chestcounter.detect.RoiScores
import com.chestcounter.detect.TemporalScores
import java.util.Locale
import kotlin.math.roundToInt

/** Tout ce qu'on sait d'un candidat à un instant donné (pour l'affichage de diagnostic). */
data class CandidateReport(
    val trackId: Int?,
    val kind: DetectionKind,
    val box: BoundingBox,
    val templateScore: Float,
    val roi: RoiScores?,
    val temporal: TemporalScores?,
    val outcome: FusionOutcome?,
    val vetoed: Boolean,
    val state: String?,
    val velocityX: Float?,
    val velocityY: Float?
)

/** Met en forme les rapports en lignes de texte courtes (écran étroit). Kotlin pur, testable. */
object DiagnosticFormatter {

    fun pct(v: Float?): String =
        if (v == null || v.isNaN()) "—" else "${(v.coerceIn(0f, 1f) * 100f).roundToInt()}%"

    fun lines(r: CandidateReport): List<String> {
        val roi = r.roi
        val t = r.temporal
        val o = r.outcome
        val out = ArrayList<String>(6)

        val kind = if (r.kind == DetectionKind.CLOSED) "FERMÉ" else "OUVERT"
        val id = r.trackId?.let { "#$it" } ?: "#–"
        val verdict = when {
            r.vetoed -> "REJETÉ"
            o != null -> "${o.status} ${pct(o.confidence)}"
            else -> "non analysé"
        }
        out.add("$id $kind  modèle ${pct(r.templateScore)}  →  $verdict")

        out.add(
            "Couleur ${pct(roi?.hsvScore)}  Canny ${pct(roi?.edgeScore)}  " +
                "Contour ${pct(roi?.contourScore)}  Géométrie ${pct(roi?.geometryScore)}  Hu ${pct(roi?.shapeScore)}"
        )
        out.add(
            "HOG ${pct(roi?.hogScore)}  ORB ${pct(roi?.orbScore)} " +
                "(${roi?.orbMatchCount?.toString() ?: "—"} corr.)  cohérence ${pct(roi?.geometricConsistency)}"
        )
        out.add(
            "Diff ${pct(t?.changeScore)}  Δ SSIM ${pct(t?.structuralChangeScore)}  " +
                "Flux ${pct(t?.motionScore)} (qualité ${pct(t?.flowQuality)})"
        )

        val vel = if (r.velocityX != null && r.velocityY != null) {
            "(${r.velocityX.roundToInt()}, ${r.velocityY.roundToInt()}) px/s"
        } else "—"
        val b = r.box
        val micros = (roi?.processingUs ?: 0L) + (t?.processingUs ?: 0L)
        out.add(
            "Kalman v=$vel  boîte ${b.left.roundToInt()},${b.top.roundToInt()} " +
                "${b.width.roundToInt()}x${b.height.roundToInt()}  état ${r.state ?: "—"}  " +
                String.format(Locale.ROOT, "%.1f ms", micros / 1000.0)
        )

        if (o != null && o.reasons.isNotEmpty()) {
            out.add("Raisons : ${o.reasons.joinToString("; ")}")
        }
        return out
    }

    fun format(reports: List<CandidateReport>): List<String> {
        val out = ArrayList<String>()
        for ((i, r) in reports.withIndex()) {
            out.addAll(lines(r))
            if (i < reports.lastIndex) out.add("──────")
        }
        return out
    }

    /** Scores non nuls d'un rapport, pour le journal structuré. */
    fun scoreMap(r: CandidateReport): Map<String, Float?> {
        val roi = r.roi
        val t = r.temporal
        return linkedMapOf(
            "tpl" to r.templateScore,
            "hsv" to roi?.hsvScore,
            "edge" to roi?.edgeScore,
            "contour" to roi?.contourScore,
            "geo" to roi?.geometryScore,
            "hu" to roi?.shapeScore,
            "hog" to roi?.hogScore,
            "orb" to roi?.orbScore,
            "diff" to t?.changeScore,
            "ssim" to t?.structuralChangeScore,
            "flow" to t?.motionScore,
            "fused" to r.outcome?.confidence
        )
    }
}
