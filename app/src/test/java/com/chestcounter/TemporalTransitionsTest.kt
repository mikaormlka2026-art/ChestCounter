package com.chestcounter

import com.chestcounter.detect.DetectionKind
import com.chestcounter.detect.TemporalScores
import com.chestcounter.detect.TemporalTransitions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalTransitionsTest {
    private fun s(change: Float?, structural: Float?) =
        TemporalScores(change, structural, null, null, null, null, null, null, 0L)

    private fun TemporalTransitions.closed(t: Long, c: Float?, st: Float?) =
        record(1, t, DetectionKind.CLOSED, s(c, st))

    private fun TemporalTransitions.open(t: Long, c: Float?, st: Float?) =
        record(1, t, DetectionKind.OPEN, s(c, st))

    @Test
    fun notRequiredAlwaysAllows() {
        val tr = TemporalTransitions()
        tr.closed(0L, 0.01f, 0.01f)
        tr.open(200L, 0.0f, 0.0f)
        assertTrue(tr.gate(1, 300L, required = false, minStrength = 0.25f))
    }

    @Test
    fun strongChangeAroundSwitchPasses() {
        val tr = TemporalTransitions()
        tr.closed(0L, 0.02f, 0.02f)
        tr.closed(100L, 0.03f, 0.02f)
        tr.open(200L, 0.8f, 0.6f)
        val ev = tr.evidence(1, 300L)
        assertNotNull(ev)
        assertEquals(200L, ev!!.switchAtMs)
        assertEquals(0.8f, ev.strength!!, 0.0001f)
        assertTrue(tr.gate(1, 300L, true, 0.25f))
    }

    @Test
    fun noChangeAroundSwitchIsBlockedWhenRequired() {
        val tr = TemporalTransitions()
        tr.closed(0L, 0.01f, 0.01f)
        tr.closed(100L, 0.02f, 0.01f)
        tr.open(200L, 0.05f, 0.02f)
        tr.open(300L, 0.03f, 0.04f)
        assertFalse(tr.gate(1, 400L, true, 0.25f))
    }

    @Test
    fun unmeasurableChangeDoesNotBlock() {
        val tr = TemporalTransitions()
        tr.closed(0L, null, null)
        tr.open(200L, null, null)
        val ev = tr.evidence(1, 300L)
        assertNotNull(ev)
        assertNull(ev!!.strength)
        assertTrue(tr.gate(1, 300L, true, 0.25f))
    }

    @Test
    fun noSwitchSeenDoesNotBlock() {
        val tr = TemporalTransitions()
        tr.closed(0L, 0.0f, 0.0f)
        tr.closed(100L, 0.0f, 0.0f)
        assertNull(tr.evidence(1, 200L))
        assertTrue(tr.gate(1, 200L, true, 0.25f))
    }

    @Test
    fun oldSwitchIsForgotten() {
        val tr = TemporalTransitions(windowMs = 3000L)
        tr.closed(0L, 0.0f, 0.0f)
        tr.open(200L, 0.9f, 0.9f)
        assertNull(tr.evidence(1, 5000L))
    }

    @Test
    fun changeJustBeforeTheSwitchIsCounted() {
        val tr = TemporalTransitions()
        tr.closed(0L, 0.9f, 0.9f)
        tr.closed(300L, 0.0f, 0.0f)
        tr.open(500L, 0.0f, 0.0f)
        assertEquals(0.9f, tr.evidence(1, 600L)!!.strength!!, 0.0001f)
    }

    @Test
    fun pruneForgetAndClearRemoveHistory() {
        val tr = TemporalTransitions()
        tr.closed(0L, 0.0f, 0.0f)
        tr.open(200L, 0.9f, 0.9f)
        tr.prune(50_000L)
        assertNull(tr.evidence(1, 50_000L))

        tr.closed(60_000L, 0.0f, 0.0f)
        tr.open(60_200L, 0.9f, 0.9f)
        tr.forget(1)
        assertNull(tr.evidence(1, 60_300L))

        tr.closed(70_000L, 0.0f, 0.0f)
        tr.open(70_200L, 0.9f, 0.9f)
        tr.clear()
        assertNull(tr.evidence(1, 70_300L))
    }
}
