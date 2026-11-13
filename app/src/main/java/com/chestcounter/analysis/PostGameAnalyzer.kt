package com.chestcounter.analysis

/** Reconstruction -> attribution -> bilan. Pure, déterministe, idempotente. */
class PostGameAnalyzer(
    private val reconstructor: OpeningReconstructor = OpeningReconstructor(),
    private val attribution: AttributionEngine = AttributionEngine()
) {
    fun analyze(
        obs: List<ObsPoint>,
        evidence: SessionEvidence = SessionEvidence(),
        sessionComplete: Boolean = true,
        incompleteReasons: List<String> = emptyList()
    ): SessionReport {
        val resolved = reconstructor.reconstruct(obs).map { attribution.resolve(it, evidence) }
        fun n(v: Verdict) = resolved.count { it.verdict == v }

        val confirmed = n(Verdict.CONFIRMED_USER)
        val probable = n(Verdict.PROBABLE_USER)
        val others = n(Verdict.OPENED_BY_OTHER)
        val unknown = n(Verdict.UNKNOWN_ATTRIBUTION)

        val opened = resolved.filter { it.recon.outcome == ReconOutcome.OPENED }
        val coverage = if (opened.isEmpty()) null
        else opened.count { it.playerCovered }.toFloat() / opened.size

        val causes = ArrayList<String>()
        if (!sessionComplete) {
            causes.add("session incomplète" + if (incompleteReasons.isEmpty()) "" else " : " + incompleteReasons.joinToString("; "))
        }
        if (evidence.player.isEmpty()) causes.add("détection du joueur indisponible : auteur des ouvertures non déterminé")
        if (evidence.otherPlayerTimes == null) causes.add("autres joueurs non analysés : aucune ouverture ne peut être « confirmée »")
        if (evidence.cues.isEmpty()) causes.add("aucun indice d'interaction analysé")
        if (unknown > 0) causes.add("$unknown ouverture(s) à auteur inconnu")

        return SessionReport(
            confirmed = confirmed,
            probable = probable,
            openedByOthers = others,
            unknown = unknown,
            alreadyOpen = n(Verdict.ALREADY_OPEN_ON_FIRST_OBSERVATION),
            rejected = n(Verdict.INVALID_EVENT),
            notOpened = n(Verdict.NOT_OPENED),
            minimum = confirmed,
            maximum = confirmed + probable + unknown,
            bestEstimate = confirmed + probable,
            coverage = coverage,
            sessionComplete = sessionComplete,
            uncertaintyCauses = causes,
            events = resolved.filter { it.verdict != Verdict.NOT_OPENED }
        )
    }
}
