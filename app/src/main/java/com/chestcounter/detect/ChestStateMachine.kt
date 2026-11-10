package com.chestcounter.detect

enum class Observation { NONE, CLOSED, OPEN }

class ChestStateMachine(
    private val confirmFrames: Int = 3,
    private val cooldownMs: Long = 3000L,
    private val resetFrames: Int = 5,
    private val armTimeoutFrames: Int = 20
) {
    private enum class State { IDLE, ARMED, COUNTED }

    private var state = State.IDLE
    private var closedStreak = 0
    private var openStreak = 0
    private var calmStreak = 0
    private var noneStreak = 0
    private var lastCountMs: Long? = null

    /** Retourne true seulement quand un nouveau coffre vient d'être compté. */
    fun update(obs: Observation, nowMs: Long): Boolean {
        when (state) {
            State.IDLE -> {
                if (obs == Observation.CLOSED) {
                    closedStreak++
                    if (closedStreak >= confirmFrames) {
                        state = State.ARMED
                        closedStreak = 0
                        openStreak = 0
                        noneStreak = 0
                    }
                } else {
                    closedStreak = 0
                }
            }

            State.ARMED -> {
                when (obs) {
                    Observation.OPEN -> {
                        noneStreak = 0
                        openStreak++
                        if (openStreak >= confirmFrames) {
                            state = State.COUNTED
                            calmStreak = 0
                            openStreak = 0
                            val last = lastCountMs
                            if (last == null || nowMs - last >= cooldownMs) {
                                lastCountMs = nowMs
                                return true
                            }
                        }
                    }
                    Observation.CLOSED -> {
                        openStreak = 0
                        noneStreak = 0
                    }
                    Observation.NONE -> {
                        openStreak = 0
                        noneStreak++
                        if (noneStreak >= armTimeoutFrames) reset()
                    }
                }
            }

            State.COUNTED -> {
                if (obs == Observation.OPEN) {
                    calmStreak = 0
                } else {
                    calmStreak++
                    if (calmStreak >= resetFrames) reset()
                }
            }
        }
        return false
    }

    fun reset() {
        state = State.IDLE
        closedStreak = 0
        openStreak = 0
        calmStreak = 0
        noneStreak = 0
    }
}
