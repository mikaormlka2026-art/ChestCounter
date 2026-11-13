package com.chestcounter

import com.chestcounter.detect.DetectionKind
import com.chestcounter.detect.TemporalScores
import com.chestcounter.detect.TemporalTransitions
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalTransitionsStrictTest {
    private fun s(change: Float?, structural: Float?) =
        TemporalScores(change, structural, null, null, null, null, null, null, 0L)

    @Test
    fun noHistoryIsRefused() {
        assertFalse(TemporalTransitions().gateStrict(1, 100L, 0.25f))
    }

    @Test
    fun noSwitchIsRefused() {
        val tr = TemporalTransitions()
        tr.record(1, 0L, DetectionKind.CLOSED, s(0.9f, 0.9f))
        tr.record(1, 100L, DetectionKind.CLOSED, s(0.9f, 0.9f))
        assertFalse(tr.gateStrict(1, 200L, 0.25f))
    }

    @Test
    fun unmeasuredChangeIsRefused() {
        val tr = TemporalTransitions()
        tr.record(1, 0L, DetectionKind.CLOSED, null)
        tr.record(1, 200L, DetectionKind.OPEN, null)
        assertFalse(tr.gateStrict(1, 300L, 0.25f))
    }

    @Test
    fun weakChangeIsRefused() {
        val tr = TemporalTransitions()
        tr.record(1, 0L, DetectionKind.CLOSED, s(0.01f, 0.01f))
        tr.record(1, 200L, DetectionKind.OPEN, s(0.05f, 0.02f))
        assertFalse(tr.gateStrict(1, 300L, 0.25f))
    }

    @Test
    fun strongMeasuredChangeIsAccepted() {
        val tr = TemporalTransitions()
        tr.record(1, 0L, DetectionKind.CLOSED, s(0.02f, 0.02f))
        tr.record(1, 200L, DetectionKind.OPEN, s(0.8f, 0.6f))
        assertTrue(tr.gateStrict(1, 300L, 0.25f))
    }
}
