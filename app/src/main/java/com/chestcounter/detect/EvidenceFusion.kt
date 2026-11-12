package com.chestcounter.detect

import com.chestcounter.config.DetectionConfig
import com.chestcounter.config.ProfileName
import java.util.Locale

enum class CandidateStatus { REJECTED, POSSIBLE, PROBABLE, CONFIRMED }

enum class EvidenceChannel { TEMPLATE, ORB, HOG, SHAPE, CONTOUR, HSV, TRACKING }

/**
 * Poids des canaux de preuve (somme = 1). Les mesures issues du même contour (Canny, contours,
 * géométrie) sont regroupées en UN canal CONTOUR avant la fusion : on ne compte pas trois fois
 * la même information.
 */
data class FusionWeights(
    val template: Float = 0.45f,
    val orb: Float = 0.12f,
    val hog: Float = 0.08f,
    val shape: Float = 0.08f,
    val contour: Float = 0.10f,
    val hsv: Float = 0.07f,
    val tracking: Float = 0.10f
) {
    fun of(c: EvidenceChannel): Float = when (c) {
        EvidenceChannel.TEMPLATE -> template
        EvidenceChannel.ORB -> orb
        EvidenceChannel.HOG -> hog
        EvidenceChannel.SHAPE -> shape
        EvidenceChannel.CONTOUR -> contour
        EvidenceChannel.HSV -> hsv
        EvidenceChannel.TRACKING -> tracking
    }

    companion object {
        fun forProfile(p: ProfileName): FusionWeights = when (p) {
            ProfileName.STRICT -> FusionWeights(0.40f, 0.14f, 0.09f, 0.09f, 0.11f, 0.07f, 0.10f)
            ProfileName.BALANCED -> FusionWeights()
            ProfileName.PERMISSIVE -> FusionWeights(0.55f, 0.10f, 0.06f, 0.06f, 0.08f, 0.05f, 0.10f)
        }
    }
}

/** Règles anti-faux-positifs. Valeurs de départ NON calibrées. */
data class FusionRules(
    /** Un canal d'apparence sous ce niveau « contredit » le modèle. */
    val vetoLevel: Float = 0.10f,
    /** Nombre de canaux d'apparence qui doivent contredire pour rejeter le candidat. */
    val vetoMinChannels: Int = 3,
    /** Au-dessus de ce score de modèle, le veto n'est pas appliqué. */
    val strongTemplate: Float = 0.90f,
    /** Nombre minimal de canaux (hors HSV) au-dessus de leur seuil pour CONFIRMER. */
    val minSupportToConfirm: Int = 2
)

data class CandidateEvidence(
    val templateScore: Float,
    /** Scores de la région (null si l'analyse n'a pas eu lieu). Les valeurs null dedans sont ignorées. */
    val roi: RoiScores? = null,
    /** Fiabilité du suivi (0..1) ; null si la piste est nouvelle. */
    val trackingScore: Float? = null
)

data class ChannelValue(
    val channel: EvidenceChannel,
    val value: Float,
    val weight: Float,
    val threshold: Float
) {
    /** Le HSV ne compte jamais comme appui : il ne peut pas confirmer un coffre. */
    val supports: Boolean get() = channel != EvidenceChannel.HSV && value >= threshold
}

data class FusionOutcome(
    val confidence: Float,
    val status: CandidateStatus,
    val channels: List<ChannelValue>,
    val supportCount: Int,
    val reasons: List<String>
) {
    fun explain(): String {
        val sb = StringBuilder()
        sb.append(String.format(Locale.ROOT, "%s (%.2f)", status, confidence))
        if (reasons.isNotEmpty()) sb.append(" : ").append(reasons.joinToString("; "))
        for (c in channels) {
            sb.append(String.format(Locale.ROOT, "\n  %s %.2f (poids %.2f, seuil %.2f)", c.channel, c.value, c.weight, c.threshold))
        }
        return sb.toString()
    }
}

/**
 * Fusion des preuves : moyenne pondérée des canaux DISPONIBLES (les canaux absents sont ignorés et
 * les poids renormalisés), puis règles de plafonnement :
 *  - le modèle seul (aucun autre canal indépendant) ne peut pas confirmer ;
 *  - il faut au moins minSupportToConfirm canaux (hors HSV) au-dessus de leur seuil ;
 *  - si plusieurs canaux d'apparence contredisent un modèle moyen, le candidat est rejeté.
 * Les preuves temporelles (SSIM, différence d'images, flux optique) ne sont PAS dans ce moteur :
 * une absence de changement est normale pour un coffre fermé ; elles servent aux transitions d'état.
 */
class EvidenceFusionEngine(
    private val cfg: DetectionConfig = DetectionConfig(),
    private val weights: FusionWeights = FusionWeights.forProfile(cfg.profile),
    private val rules: FusionRules = FusionRules()
) {
    fun evaluate(e: CandidateEvidence): FusionOutcome {
        val roi = e.roi
        val ch = ArrayList<ChannelValue>(7)

        fun add(c: EvidenceChannel, v: Float, threshold: Float) {
            ch += ChannelValue(c, clamp01(v), weights.of(c), threshold)
        }

        add(EvidenceChannel.TEMPLATE, e.templateScore, cfg.templateThreshold)
        if (roi != null) {
            if (cfg.orbEnabled) roi.orbScore?.let { add(EvidenceChannel.ORB, it, cfg.orbThreshold) }
            if (cfg.hogEnabled) roi.hogScore?.let { add(EvidenceChannel.HOG, it, cfg.hogThreshold) }
            roi.shapeScore?.let { add(EvidenceChannel.SHAPE, it, cfg.huThreshold) }
            val contourParts = listOfNotNull(roi.contourScore, roi.geometryScore, roi.edgeScore)
            if (contourParts.isNotEmpty()) {
                add(EvidenceChannel.CONTOUR, contourParts.average().toFloat(), cfg.edgeThreshold)
            }
            roi.hsvScore?.let { add(EvidenceChannel.HSV, it, cfg.hsvThreshold) }
        }
        e.trackingScore?.let { add(EvidenceChannel.TRACKING, it, cfg.trackingThreshold) }

        val totalWeight = ch.sumOf { it.weight.toDouble() }
        if (totalWeight <= 1e-9) {
            return FusionOutcome(0f, CandidateStatus.REJECTED, ch, 0, listOf("aucun poids exploitable"))
        }
        val confidence = (ch.sumOf { (it.weight * it.value).toDouble() } / totalWeight).toFloat()

        var status = when {
            confidence >= cfg.confirmedThreshold -> CandidateStatus.CONFIRMED
            confidence >= cfg.probableThreshold -> CandidateStatus.PROBABLE
            confidence >= cfg.possibleThreshold -> CandidateStatus.POSSIBLE
            else -> CandidateStatus.REJECTED
        }

        val reasons = ArrayList<String>()
        val supportCount = ch.count { it.supports }
        val independent = ch.count { it.channel != EvidenceChannel.TEMPLATE && it.channel != EvidenceChannel.HSV }

        if (status == CandidateStatus.CONFIRMED && independent == 0) {
            status = CandidateStatus.PROBABLE
            reasons += "preuve unique : le modèle seul ne confirme pas"
        }
        if (status == CandidateStatus.CONFIRMED && supportCount < rules.minSupportToConfirm) {
            status = CandidateStatus.PROBABLE
            reasons += "support insuffisant ($supportCount/${rules.minSupportToConfirm})"
        }

        val appearance = setOf(EvidenceChannel.ORB, EvidenceChannel.HOG, EvidenceChannel.SHAPE, EvidenceChannel.CONTOUR)
        val contradicting = ch.count { it.channel in appearance && it.value < rules.vetoLevel }
        if (contradicting >= rules.vetoMinChannels && e.templateScore < rules.strongTemplate) {
            status = CandidateStatus.REJECTED
            reasons += "$contradicting canaux d'apparence contredisent le modèle"
        }

        return FusionOutcome(confidence, status, ch, supportCount, reasons)
    }

    private fun clamp01(x: Float): Float = if (x < 0f) 0f else if (x > 1f) 1f else x
}
