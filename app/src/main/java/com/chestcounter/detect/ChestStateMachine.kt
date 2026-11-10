package com.chestcounter.detect

enum class ChestState { IDLE, CLOSED_SEEN, COOLDOWN }

data class Observation(
    val timeMs: Long,
    val closedScore: Float,
    val openScore: Float
)

data class StateConfig(
    val closedThreshold: Float = 0.70f,
    val openThreshold: Float = 0.70f,
    val closedStableMs: Long = 300L,
    val openStableMs: Long = 300L,
    val maxClosedToOpenMs: Long = 4000L,
    val cooldownMs: Long = 5000L,
    val rearmGapMs: Long = 1500L,
    val gapToleranceMs: Long = 250L
)

/**
 * Machine à états sans dépendance Android (donc testable).
 * Un coffre est compté seulement si : "fermé" stable, puis "ouvert" stable
 * dans un délai court. Ensuite : pause, puis réarmement uniquement
 * quand ni fermé ni ouvert n'est visible pendant un moment.
 */
class ChestStateMachine(private val cfg: StateConfig = StateConfig()) {

    var state: ChestState = ChestState.IDLE
        private set
    var count: Int = 0
        private set
    var lastConfidence: Float = 0f
        private set

    private val closedStreak = Streak(cfg.gapToleranceMs)
    private val openStreak = Streak(cfg.gapToleranceMs)
    private var lastClosedAt = -1L
    private var closedPeak = 0f
    private var cooldownUntil = 0L
    private var noneSince = -1L

    /** Retourne true uniquement au moment où un nouveau coffre est compté. */
    fun process(o: Observation): Boolean {
        val t = o.timeMs
        val closedNow = o.closedScore >= cfg.closedThreshold && o.closedScore > o.openScore
        val openNow = o.openScore >= cfg.openThreshold && o.openScore > o.closedScore

        val closedDur = closedStreak.update(closedNow, t)
        val openDur = openStreak.update(openNow, t)

        when (state) {
            ChestState.IDLE -> {
                if (closedDur >= cfg.closedStableMs) {
                    state = ChestState.CLOSED_SEEN
                    lastClosedAt = t
                    closedPeak = o.closedScore
                }
            }

            ChestState.CLOSED_SEEN -> {
                if (closedNow) {
                    lastClosedAt = t
                    if (o.closedScore > closedPeak) closedPeak = o.closedScore
                }
                if (openDur >= cfg.openStableMs && t - lastClosedAt <= cfg.maxClosedToOpenMs) {
                    count++
                    lastConfidence = (closedPeak + o.openScore) / 2f
                    state = ChestState.COOLDOWN
                    cooldownUntil = t + cfg.cooldownMs
                    noneSince = -1L
                    closedStreak.reset()
                    openStreak.reset()
                    return true
                } else if (t - lastClosedAt > cfg.maxClosedToOpenMs) {
                    state = ChestState.IDLE
                    closedStreak.reset()
                }
            }

            ChestState.COOLDOWN -> {
                if (t >= cooldownUntil) {
                    if (!closedNow && !openNow) {
                        if (noneSince < 0) noneSince = t
                        if (t - noneSince >= cfg.rearmGapMs) {
                            state = ChestState.IDLE
                            closedStreak.reset()
                            openStreak.reset()
                        }
                    } else {
                        noneSince = -1L
                    }
                }
            }
        }
        return false
    }

    fun reset() {
        state = ChestState.IDLE
        count = 0
        lastConfidence = 0f
        lastClosedAt = -1L
        closedPeak = 0f
        cooldownUntil = 0L
        noneSince = -1L
        closedStreak.reset()
        openStreak.reset()
    }

    private class Streak(private val toleranceMs: Long) {
        private var start = -1L
        private var lastHit = -1L

        fun update(hit: Boolean, t: Long): Long {
            if (hit) {
                if (start < 0 || t - lastHit > toleranceMs) start = t
                lastHit = t
                return t - start
            }
            if (start >= 0 && t - lastHit > toleranceMs) start = -1L
            return if (start >= 0) lastHit - start else 0L
        }

        fun reset() {
            start = -1L
            lastHit = -1L
        }
    }
}
