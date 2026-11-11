package com.chestcounter.fusion

import java.util.Locale

class FusionConfig(
    val weights: Map<Source, Double> = mapOf(
        Source.VISION to 0.40, Source.TEMPLATE to 0.15, Source.SILHOUETTE to 0.10,
        Source.COLOR to 0.10, Source.OCR to 0.10, Source.TEMPORAL to 0.25, Source.AUDIO to 0.10),
    val maxAgeMs: Long = 1000,
    val thresholds: Map<Claim, Double> = Claim.values().associateWith { if (it == Claim.OPENING) 0.55 else 0.60 },
    /** Nombre minimal de sources distinctes qui soutiennent la décision. */
    val minSupport: Map<Claim, Int> = Claim.values().associateWith { if (it == Claim.ABSENT) 1 else 2 },
    val supportLevel: Double = 0.50,
    val contradictionLevel: Double = 0.30,
)

data class FusionResult(
    val scores: Map<Claim, Double>,
    val decision: Claim?,
    val confirmed: Boolean,
    val confidence: Double,
    val support: Set<Source>,
    val contradiction: Boolean,
    val ignored: List<String>,
    val explanation: String,
)

/**
 * Fusion pondérée : poids de base du module x confiance x qualité x fiabilité réglable x fraîcheur.
 * Seul le dernier instantané de chaque module compte. Le score d'une affirmation est la part du
 * poids total qui la soutient : un indice contraire d'un autre module diminue donc le score.
 * Une décision n'est confirmée que si le score dépasse le seuil, si assez de modules distincts
 * la soutiennent et s'il n'y a pas de contradiction forte.
 */
class FusionEngine(val config: FusionConfig = FusionConfig()) {
    private val reliability = HashMap<Source, Double>()
    fun setReliability(s: Source, r: Double) { reliability[s] = r.coerceIn(0.0, 1.0) }
    private fun f(x: Double) = String.format(Locale.ROOT, "%.2f", x)

    fun fuse(all: List<Evidence>, now: Long, unavailable: Set<Source> = emptySet()): FusionResult {
        val ignored = LinkedHashSet<String>()
        val fresh = ArrayList<Evidence>()
        for (e in all) when {
            e.source in unavailable -> ignored += "${e.source} : module indisponible"
            now - e.tMs > config.maxAgeMs -> ignored += "${e.source} : indice trop ancien"
            else -> fresh += e
        }
        val latest = HashMap<Source, Long>()
        for (e in fresh) latest[e.source] = maxOf(latest[e.source] ?: Long.MIN_VALUE, e.tMs)
        val used = fresh.filter { it.tMs == latest[it.source] }

        val ws = used.map { e ->
            val age = (now - e.tMs).coerceAtLeast(0).toDouble()
            val decay = (1.0 - age / config.maxAgeMs).coerceIn(0.0, 1.0)
            val w = (config.weights[e.source] ?: 0.0) * e.confidence.coerceIn(0.0, 1.0) *
                e.quality.coerceIn(0.0, 1.0) * (reliability[e.source] ?: 1.0) * decay
            e to w
        }.filter { it.second > 1e-9 }
        val total = ws.sumOf { it.second }
        if (total <= 1e-9) {
            return FusionResult(Claim.values().associateWith { 0.0 }, null, false, 0.0, emptySet(), false,
                ignored.toList(), "Aucun indice exploitable" + if (ignored.isNotEmpty()) " (${ignored.joinToString("; ")})" else "")
        }
        val scores = Claim.values().associateWith { c ->
            ws.filter { it.first.claim == c }.sumOf { it.second * it.first.value.coerceIn(0.0, 1.0) } / total
        }
        val support = Claim.values().associateWith { c ->
            ws.filter { it.first.claim == c && it.first.value >= config.supportLevel }.map { it.first.source }.toSet()
        }
        val best = Claim.values().maxByOrNull { scores.getValue(it) }!!
        val cl = config.contradictionLevel
        val openFamily = maxOf(scores.getValue(Claim.OPENED), scores.getValue(Claim.OPENING), scores.getValue(Claim.TREASURE))
        val groups = listOf(scores.getValue(Claim.ABSENT), scores.getValue(Claim.CLOSED), openFamily)
        val contradiction = groups.count { it >= cl } >= 2

        val score = scores.getValue(best)
        val sup = support.getValue(best)
        val reason = when {
            score < (config.thresholds[best] ?: 0.6) -> "score sous le seuil"
            sup.size < (config.minSupport[best] ?: 2) -> "support insuffisant (${sup.size}/${config.minSupport[best] ?: 2} sources)"
            contradiction -> "contradiction entre indices"
            else -> null
        }
        val sb = StringBuilder("Décision $best (${f(score)}) ")
        sb.append(if (reason == null) "confirmée" else "non confirmée : $reason").append('\n')
        for ((e, w) in ws) sb.append("  ${e.source} -> ${e.claim} val=${f(e.value)} conf=${f(e.confidence)} poids=${f(w)}\n")
        if (ignored.isNotEmpty()) sb.append("  ignorés : ${ignored.joinToString("; ")}\n")
        return FusionResult(scores, best, reason == null, score, sup, contradiction, ignored.toList(), sb.toString())
    }
}
