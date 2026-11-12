package com.chestcounter.detect

enum class DetectionKind { CLOSED, OPEN }

/** Résultat de l'analyse classique d'un candidat (null si le candidat n'a pas été analysé). */
data class CandidateAnalysis(
    val roi: RoiScores?,
    val outcome: FusionOutcome?,
    val vetoed: Boolean
)

data class Detection(
    val kind: DetectionKind,
    val box: BoundingBox,
    val score: Float,
    val analysis: CandidateAnalysis? = null
)

data class DetectionResult(
    val closedScore: Float,
    val openScore: Float,
    val processingMs: Long,
    val error: String? = null,
    val detections: List<Detection> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0,
    /** Candidats écartés par le garde-fou (gardés pour le diagnostic). */
    val vetoed: List<Detection> = emptyList(),
    val analysisUs: Long = 0L
)

fun nonMaxSuppression(
    items: List<Detection>,
    iouThreshold: Float,
    maxCount: Int
): List<Detection> {
    val sorted = items.sortedByDescending { it.score }
    val kept = ArrayList<Detection>()
    for (d in sorted) {
        if (kept.size >= maxCount) break
        val overlaps = kept.any { it.kind == d.kind && it.box.iou(d.box) > iouThreshold }
        if (!overlaps) kept.add(d)
    }
    return kept
}
