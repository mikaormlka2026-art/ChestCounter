package com.chestcounter.fusion

import com.chestcounter.detect.DetectionKind
import com.chestcounter.track.Track

enum class ChestPhase { DETECTED, OPENING, OPENED, TREASURE_FOUND, LOST, COOLDOWN }

data class PhaseConfig(
    val openStableMs: Long = 300L,
    val goldThreshold: Float = 0.5f,
    val goldStableMs: Long = 200L,
    val cooldownMs: Long = 5000L
)

data class PhaseEvent(
    val trackId: Int,
    val from: ChestPhase?,
    val to: ChestPhase,
    val timeMs: Long,
    val sawClosed: Boolean
)

class ChestPhaseTracker(private val cfg: PhaseConfig = PhaseConfig()) {

    private class State(
        var phase: ChestPhase,
        val sawClosed: Boolean,
        var openSince: Long = -1L,
        var goldSince: Long = -1L,
        var cooldownUntil: Long = 0L
    )

    private val states = HashMap<Int, State>()

    fun phaseOf(id: Int): ChestPhase? = states[id]?.phase

    fun update(
        tracks: List<Track>,
        gold: Map<Int, Float>,
        lostIds: List<Int>,
        nowMs: Long
    ): List<PhaseEvent> {
        val out = ArrayList<PhaseEvent>()

        val iter = states.entries.iterator()
        while (iter.hasNext()) {
            val s = iter.next().value
            if (s.phase == ChestPhase.COOLDOWN && nowMs >= s.cooldownUntil) iter.remove()
        }

        for (t in tracks) {
            var st = states[t.id]
            if (st == null) {
                if (t.kind == DetectionKind.CLOSED) {
                    st = State(ChestPhase.DETECTED, true)
                    out.add(PhaseEvent(t.id, null, ChestPhase.DETECTED, nowMs, true))
                } else {
                    st = State(ChestPhase.OPENED, false, openSince = nowMs)
                    out.add(PhaseEvent(t.id, null, ChestPhase.OPENED, nowMs, false))
                }
                states[t.id] = st
                continue
            }

            when (st.phase) {
                ChestPhase.DETECTED -> {
                    if (t.visible && t.kind == DetectionKind.OPEN) {
                        st.openSince = nowMs
                        move(t.id, st, ChestPhase.OPENING, nowMs, out)
                    }
                }

                ChestPhase.OPENING -> {
                    if (t.visible && t.kind == DetectionKind.OPEN) {
                        if (nowMs - st.openSince >= cfg.openStableMs) {
                            move(t.id, st, ChestPhase.OPENED, nowMs, out)
                        }
                    } else if (t.visible && t.kind == DetectionKind.CLOSED) {
                        st.openSince = -1L
                        move(t.id, st, ChestPhase.DETECTED, nowMs, out)
                    }
                }

                ChestPhase.OPENED -> {
                    val g = gold[t.id] ?: 0f
                    if (t.visible && g >= cfg.goldThreshold) {
                        if (st.goldSince < 0L) st.goldSince = nowMs
                        if (nowMs - st.goldSince >= cfg.goldStableMs) {
                            move(t.id, st, ChestPhase.TREASURE_FOUND, nowMs, out)
                        }
                    } else {
                        st.goldSince = -1L
                    }
                }

                else -> {}
            }
        }

        for (id in lostIds) {
            val st = states[id] ?: continue
            when (st.phase) {
                ChestPhase.DETECTED, ChestPhase.OPENING -> {
                    move(id, st, ChestPhase.LOST, nowMs, out)
                    states.remove(id)
                }

                ChestPhase.OPENED, ChestPhase.TREASURE_FOUND -> {
                    st.cooldownUntil = nowMs + cfg.cooldownMs
                    move(id, st, ChestPhase.COOLDOWN, nowMs, out)
                }

                else -> {}
            }
        }
        return out
    }

    fun reset() {
        states.clear()
    }

    private fun move(
        id: Int,
        st: State,
        to: ChestPhase,
        nowMs: Long,
        out: MutableList<PhaseEvent>
    ) {
        out.add(PhaseEvent(id, st.phase, to, nowMs, st.sawClosed))
        st.phase = to
    }
}
