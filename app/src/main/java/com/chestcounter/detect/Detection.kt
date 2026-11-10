package com.chestcounter.detect

enum class DetectionKind { CLOSED, OPEN }

data class Detection(
    val kind: DetectionKind,
    val box: BoundingBox,
    val score: Float
)

data class DetectionResult(
    val closedScore: Float,
    val openScore: Float,
    val processingMs: Long,
    val error: String? = null,
    val detections: List<Detection> = emptyList(),
    val frameWidth: Int = 0,
    val frameHeight: Int = 0
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
