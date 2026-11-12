package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.fusion.ChestEngine
import com.chestcounter.fusion.CountDeduplicator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChestEngineGuardsTest {

    private fun det(kind: DetectionKind, x: Float, y: Float) =
        Detection(kind, BoundingBox.fromSize(x, y, 50f, 50f), 0.9f)

    private fun ChestEngine.run(kind: DetectionKind, from: Long, to: Long, x: Float, y: Float) {
        var t = from
        while (t <= to) {
            process(listOf(det(kind, x, y)), t) { 0f }
            t += 100L
        }
    }

    // Un cycle fermé -> ouvert : le comptage a lieu à base + 500 ms.
    private fun ChestEngine.cycle(base: Long, x: Float, y: Float) {
        run(DetectionKind.CLOSED, base, base + 100L, x, y)
        run(DetectionKind.OPEN, base + 200L, base + 500L, x, y)
    }

    @Test
    fun sameSpotShortlyAfterIsNotCountedTwice() {
        val e = ChestEngine(dedup = CountDeduplicator(windowMs = 3000L))
        e.cycle(0L, 100f, 100f)
        assertEquals(1, e.count)
        e.process(emptyList(), 2100L) { 0f }
        e.cycle(2200L, 100f, 100f)
        assertEquals(1, e.count)
        assertEquals(1, e.suppressedDuplicates)
    }

    @Test
    fun anotherSpotIsCounted() {
        val e = ChestEngine(dedup = CountDeduplicator(windowMs = 3000L))
        e.cycle(0L, 100f, 100f)
        e.process(emptyList(), 2100L) { 0f }
        e.cycle(2200L, 500f, 300f)
        assertEquals(2, e.count)
        assertEquals(0, e.suppressedDuplicates)
    }

    @Test
    fun sameSpotAfterTheWindowIsCounted() {
        val e = ChestEngine(dedup = CountDeduplicator(windowMs = 1000L))
        e.cycle(0L, 100f, 100f)
        e.process(emptyList(), 2100L) { 0f }
        e.cycle(2200L, 100f, 100f)
        assertEquals(2, e.count)
    }

    @Test
    fun withoutDedupBehaviourIsUnchanged() {
        val e = ChestEngine()
        e.cycle(0L, 100f, 100f)
        e.process(emptyList(), 2100L) { 0f }
        e.cycle(2200L, 100f, 100f)
        assertEquals(2, e.count)
    }

    @Test
    fun closedGateBlocksCounting() {
        val e = ChestEngine(countGate = { _, _ -> false })
        e.cycle(0L, 100f, 100f)
        assertEquals(0, e.count)
        assertEquals(1, e.gatedCounts)
    }

    @Test
    fun gateReceivesTrackIdAndTime() {
        val calls = ArrayList<Pair<Int, Long>>()
        val e = ChestEngine(countGate = { id, t -> calls.add(id to t); true })
        e.cycle(0L, 100f, 100f)
        assertEquals(1, e.count)
        assertEquals(1, calls.size)
        assertTrue(calls[0].first > 0)
        assertEquals(500L, calls[0].second)
    }

    @Test
    fun resetCallsHookAndClearsDedup() {
        var resets = 0
        val e = ChestEngine(dedup = CountDeduplicator(windowMs = 3000L), onReset = { resets++ })
        e.cycle(0L, 100f, 100f)
        assertEquals(1, e.count)
        e.reset()
        assertEquals(1, resets)
        assertEquals(0, e.count)
        e.cycle(600L, 100f, 100f)
        assertEquals(1, e.count)
    }
}
