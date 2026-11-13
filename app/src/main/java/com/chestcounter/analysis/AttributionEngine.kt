package com.chestcounter.analysis

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** Valeurs de départ NON calibrées. */
data class AttributionConfig(
    val windowBeforeMs: Long = 2000L,
    val windowAfterMs: Long = 500L,
    val minPlayerConfidence: Float = 0.5f,
    val minPlayerCoverage: Float = 0.6f,
    /** Distance centre à centre rapportée à la taille du coffre. */
    val nearFactor: Float = 4.0f,
    val minNearFraction: Float = 0.7f,
    val cueWindowBeforeMs: Long = 3000L,
    val cueMinStrength: Float = 0.5f
)

/**
 * CONFIRMED_USER exige : joueur identifiable, proximité soutenue, indice d'interaction,
 * ET autres joueurs analysés et absents. Une donnée manquante ne confirme jamais.
 */
class AttributionEngine(private val cfg: AttributionConfig = AttributionConfig()) {

    fun resolve(r: Reconstructed, ev: SessionEvidence): ResolvedEvent {
        when (r.outcome) {
            ReconOutcome.ALREADY_OPEN_ON_FIRST_OBSERVATION ->
                return ResolvedEvent(r, Verdict.ALREADY_OPEN_ON_FIRST_OBSERVATION, r.reason, false)
            ReconOutcome.NOT_OPENED -> return ResolvedEvent(r, Verdict.NOT_OPENED, r.reason, false)
            ReconOutcome.INVALID -> return ResolvedEvent(r, Verdict.INVALID_EVENT, r.reason, false)
            ReconOutcome.OPENED -> Unit
        }
        val openMs = r.firstOpenMs
            ?: return ResolvedEvent(r, Verdict.INVALID_EVENT, "instant d'ouverture absent", false)

        val t0 = openMs - cfg.windowBeforeMs
        val t1 = openMs + cfg.windowAfterMs
        val samples = ev.player.filter { it.tMs in t0..t1 }
        if (samples.isEmpty()) return unknown(r, "aucune observation du joueur autour de l'ouverture", false)

        val boxes = samples
            .filter { it.confidence >= cfg.minPlayerConfidence }
            .mapNotNull { it.box }
        val coverage = boxes.size.toFloat() / samples.size
        if (coverage < cfg.minPlayerCoverage) {
            return unknown(r, "joueur non identifiable (couverture ${pct(coverage)})", false)
        }

        val chestSize = max(r.box.width, r.box.height)
        if (chestSize <= 0f) return unknown(r, "taille de coffre invalide", true)

        val near = boxes.count {
            hypot(it.centerX - r.box.centerX, it.centerY - r.box.centerY) / chestSize <= cfg.nearFactor
        }
        val nearFraction = near.toFloat() / boxes.size
        val others: Boolean? = ev.otherPlayerTimes?.any { it in t0..t1 }
        val cue = ev.cues.any {
            it.tMs in (openMs - cfg.cueWindowBeforeMs)..t1 && it.strength >= cfg.cueMinStrength
        }

        if (nearFraction < cfg.minNearFraction) {
            return if (others == true && !cue) {
                ResolvedEvent(r, Verdict.OPENED_BY_OTHER,
                    "joueur éloigné (proximité ${pct(nearFraction)}) et autre joueur observé", true)
            } else {
                ResolvedEvent(r, Verdict.UNKNOWN_ATTRIBUTION,
                    "joueur éloigné (proximité ${pct(nearFraction)}) : auteur non identifiable", true)
            }
        }
        if (others == true) {
            return ResolvedEvent(r, Verdict.UNKNOWN_ATTRIBUTION,
                "autre joueur observé près du coffre : auteur ambigu", true)
        }
        return if (cue && others == false) {
            ResolvedEvent(r, Verdict.CONFIRMED_USER,
                "proximité soutenue (${pct(nearFraction)}), indice d'interaction, aucun autre joueur", true)
        } else {
            val missing = ArrayList<String>()
            if (!cue) missing.add("indice d'interaction")
            if (others == null) missing.add("analyse des autres joueurs")
            ResolvedEvent(r, Verdict.PROBABLE_USER,
                "proximité soutenue (${pct(nearFraction)}) ; manque : ${missing.joinToString(", ")}", true)
        }
    }

    private fun unknown(r: Reconstructed, why: String, covered: Boolean) =
        ResolvedEvent(r, Verdict.UNKNOWN_ATTRIBUTION, why, covered)

    private fun pct(x: Float): String = "${(x * 100).roundToInt()} %"
}
