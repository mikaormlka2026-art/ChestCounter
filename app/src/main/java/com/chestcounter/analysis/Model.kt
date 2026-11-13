package com.chestcounter.analysis

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind

/** Résultat d'attribution. UNKNOWN_ATTRIBUTION n'est JAMAIS promu en CONFIRMED_USER. */
enum class Verdict {
    CONFIRMED_USER,
    PROBABLE_USER,
    OPENED_BY_OTHER,
    UNKNOWN_ATTRIBUTION,
    ALREADY_OPEN_ON_FIRST_OBSERVATION,
    NOT_OPENED,
    INVALID_EVENT
}

enum class ReconOutcome { OPENED, ALREADY_OPEN_ON_FIRST_OBSERVATION, NOT_OPENED, INVALID }

data class ObsPoint(
    val epoch: Int,
    val trackId: Int,
    val tMs: Long,
    val kind: DetectionKind,
    val box: BoundingBox,
    val score: Float
)

/** box == null : joueur non identifiable à cet instant. */
data class PlayerSample(val tMs: Long, val box: BoundingBox?, val confidence: Float)

data class InteractionCue(val tMs: Long, val strength: Float, val label: String)

/** Un module absent donne une valeur « inconnue », jamais une preuve positive. */
data class SessionEvidence(
    val player: List<PlayerSample> = emptyList(),
    val cues: List<InteractionCue> = emptyList(),
    /** null = détection des autres joueurs indisponible ; liste vide = analysée, aucun autre joueur vu. */
    val otherPlayerTimes: List<Long>? = null
)

data class Reconstructed(
    val epoch: Int,
    val trackId: Int,
    val box: BoundingBox,
    val lastClosedMs: Long?,
    val firstOpenMs: Long?,
    val closedObs: Int,
    val openObs: Int,
    val outcome: ReconOutcome,
    val reason: String
)

data class ResolvedEvent(
    val recon: Reconstructed,
    val verdict: Verdict,
    val reason: String,
    val playerCovered: Boolean
)

data class SessionReport(
    val confirmed: Int,
    val probable: Int,
    val openedByOthers: Int,
    val unknown: Int,
    val alreadyOpen: Int,
    val rejected: Int,
    val notOpened: Int,
    val minimum: Int,
    val maximum: Int,
    val bestEstimate: Int,
    val coverage: Float?,
    val sessionComplete: Boolean,
    val uncertaintyCauses: List<String>,
    val events: List<ResolvedEvent>
)
