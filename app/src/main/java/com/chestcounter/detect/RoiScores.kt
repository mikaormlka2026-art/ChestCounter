package com.chestcounter.detect

/**
 * Scores des méthodes de vision classique sur une région candidate.
 * null = méthode indisponible sur cette frame (la fusion doit l'ignorer, pas la compter comme 0).
 */
data class RoiScores(
    val hsvScore: Float?,
    val edgeScore: Float?,
    val contourScore: Float?,
    val geometryScore: Float?,
    val shapeScore: Float?,
    val processingUs: Long,
    val error: String? = null
) {
    companion object {
        fun none(processingUs: Long, error: String? = null) =
            RoiScores(null, null, null, null, null, processingUs, error)
    }
}
