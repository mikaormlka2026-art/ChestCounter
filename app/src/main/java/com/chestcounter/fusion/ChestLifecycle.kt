package com.chestcounter.fusion

enum class ChestState { ABSENT, DETECTED, OPENING, OPENED, TREASURE_FOUND, LOST, COOLDOWN }

sealed class ChestEvent {
    object None : ChestEvent()
    data class Counted(val id: Int, val confidence: Double, val explanation: String) : ChestEvent()
    /** Coffre vu déjà ouvert à sa première apparition : jamais compté. */
    data class IgnoredAlreadyOpen(val id: Int) : ChestEvent()
    data class Uncertain(val id: Int, val reason: String) : ChestEvent()
    data class Expired(val id: Int) : ChestEvent()
}

data class LifecycleConfig(
    val closedFrames: Int = 2, val openedFrames: Int = 2,
    val openingTimeoutMs: Long = 4000, val lostAfterMs: Long = 800,
    val cooldownMs: Long = 5000, val rearmFrames: Int = 6,
)

/** Machine d'états d'un coffre suivi. Un coffre n'est compté qu'une fois par cycle. */
class ChestLifecycle(val id: Int, private val c: LifecycleConfig = LifecycleConfig()) {
    var state = ChestState.ABSENT; private set
    private var consumed = false
    private var closedRun = 0
    private var openedRun = 0
    private var lastSeen = -1L
    private var stateSince = 0L
    private var resume = ChestState.ABSENT

    private fun go(s: ChestState, t: Long) { state = s; stateSince = t }

    /** r = résultat de fusion pour ce coffre ; null = coffre non vu dans cette image. */
    fun update(r: FusionResult?, t: Long): ChestEvent {
        if (r == null) return unseen(t)
        lastSeen = t
        if (state == ChestState.LOST) go(resume, t)
        val d = if (r.confirmed) r.decision else null
        closedRun = if (d == Claim.CLOSED) closedRun + 1 else 0
        openedRun = if (d == Claim.OPENED) openedRun + 1 else 0

        when (state) {
            ChestState.ABSENT -> when {
                d == Claim.OPENING -> go(ChestState.OPENING, t)
                closedRun >= c.closedFrames -> go(ChestState.DETECTED, t)
                openedRun >= c.openedFrames -> { consumed = true; go(ChestState.OPENED, t); return ChestEvent.IgnoredAlreadyOpen(id) }
            }
            ChestState.DETECTED -> when {
                d == Claim.OPENING -> go(ChestState.OPENING, t)
                openedRun >= c.openedFrames -> return count(r, t)
            }
            ChestState.OPENING -> when {
                openedRun >= c.openedFrames -> return count(r, t)
                closedRun >= c.closedFrames -> go(ChestState.DETECTED, t)
                t - stateSince > c.openingTimeoutMs -> { go(ChestState.DETECTED, t); return ChestEvent.Uncertain(id, "ouverture non confirmée avant le délai") }
            }
            ChestState.OPENED, ChestState.TREASURE_FOUND -> when {
                state == ChestState.OPENED && d == Claim.TREASURE -> go(ChestState.TREASURE_FOUND, t)
                t - stateSince >= c.cooldownMs -> go(ChestState.COOLDOWN, t)
            }
            ChestState.COOLDOWN -> if (closedRun >= c.rearmFrames) { consumed = false; go(ChestState.DETECTED, t) }
            ChestState.LOST -> {}
        }
        return ChestEvent.None
    }

    private fun count(r: FusionResult, t: Long): ChestEvent {
        if (consumed) return ChestEvent.None
        consumed = true; go(ChestState.OPENED, t)
        return ChestEvent.Counted(id, r.confidence, r.explanation)
    }

    private fun unseen(t: Long): ChestEvent {
        if (state == ChestState.ABSENT || state == ChestState.LOST) return ChestEvent.None
        if (lastSeen >= 0 && t - lastSeen > c.lostAfterMs) { resume = state; go(ChestState.LOST, t) }
        return ChestEvent.None
    }

    /** Appelé quand le tracker oublie ce coffre. */
    fun expire(): ChestEvent {
        val wasOpening = state == ChestState.OPENING || (state == ChestState.LOST && resume == ChestState.OPENING)
        state = ChestState.ABSENT
        return if (wasOpening && !consumed) ChestEvent.Uncertain(id, "disparu pendant l'ouverture") else ChestEvent.Expired(id)
    }
}
