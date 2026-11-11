package com.chestcounter.detect

/**
 * Résultats de l'analyse temporelle d'une région (comparaison avec l'observation précédente
 * du même coffre suivi). null = non mesurable : pas d'historique, écart de temps trop grand,
 * ROI de taille incohérente ou erreur. La fusion doit ignorer un null, pas le compter comme 0.
 */
data class TemporalScores(
    /** Différence d'images après compensation du déplacement : part de pixels qui ont changé (0..1). */
    val changeScore: Float?,
    /** Changement structurel par SSIM : 0 = identique, 1 = très différent. */
    val structuralChangeScore: Float?,
    /** Mouvement local résiduel, une fois le mouvement d'ensemble (caméra) retiré (0..1). */
    val motionScore: Float?,
    /** Fiabilité du flux optique (0..1) : points bien suivis, mouvements cohérents entre eux. */
    val flowQuality: Float?,
    /** SSIM brut (-1..1), conservé pour le diagnostic. */
    val ssim: Float?,
    /** Déplacement d'ensemble estimé de la ROI normalisée, en pixels. */
    val shiftX: Float?,
    val shiftY: Float?,
    /** Temps écoulé depuis l'observation précédente utilisée. */
    val gapMs: Long?,
    val processingUs: Long,
    val error: String? = null
) {
    companion object {
        fun none(processingUs: Long, error: String? = null) =
            TemporalScores(null, null, null, null, null, null, null, null, processingUs, error)
    }
}
