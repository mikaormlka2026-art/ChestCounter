package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.fusion.ChestEngine
import org.junit.Assert.assertEquals
import org.junit.Test

class ChestEnginePendingGateTest {

    private fun det(kind: DetectionKind) =
        Detection(kind, BoundingBox.fromSize(100f, 100f, 50f, 50f), 0.9f)

    private fun ChestEngine.run(kind: DetectionKind, from: Long, to: Long) {
        var t = from
        while (t <= to) {
            process(listOf(det(kind)), t) { 0f }
            t += 100L
        }
    }

    // L'ouverture est détectée à 500 ms.
    private fun ChestEngine.cycle() {
        run(DetectionKind.CLOSED, 0L, 100L)
        run(DetectionKind.OPEN, 200L, 500L)
    }

    @Test
    fun refusedThenAcceptedWithinWindowIsCountedOnce() {
        var allowed = false
        val e = ChestEngine(countGate = { _, _ -> allowed })
        e.cycle()
        assertEquals(0, e.count)
        assertEquals(1, e.gatedCounts)
        assertEquals(1, e.pendingCount)

        allowed = true
        e.run(DetectionKind.OPEN, 600L, 2000L)
        assertEquals(1, e.count)
        assertEquals(0, e.pendingCount)
        assertEquals(0, e.expiredPending)
    }

    @Test
    fun neverAcceptedExpiresAndIsNeverCounted() {
        val e = ChestEngine(countGate = { _, _ -> false })
        e.cycle()
        e.run(DetectionKind.OPEN, 600L, 4000L)
        assertEquals(0, e.count)
        assertEquals(1, e.gatedCounts)
        assertEquals(1, e.expiredPending)
        assertEquals(0, e.pendingCount)
    }

    @Test
    fun acceptanceAfterTheWindowDoesNotCount() {
        var allowed = false
        val e = ChestEngine(countGate = { _, _ -> allowed })
        e.cycle()
        e.run(DetectionKind.OPEN, 600L, 4000L)
        allowed = true
        e.run(DetectionKind.OPEN, 4100L, 5000L)
        assertEquals(0, e.count)
    }

    @Test
    fun trackLostWhilePendingIsDropped() {
        var allowed = false
        val e = ChestEngine(countGate = { _, _ -> allowed })
        e.cycle()
        e.process(emptyList(), 2500L) { 0f }
        assertEquals(1, e.expiredPending)
        allowed = true
        e.process(emptyList(), 2600L) { 0f }
        assertEquals(0, e.count)
    }

    @Test
    fun gateIsNotCalledTwiceInTheSameFrameForTheSameChest() {
        var calls = 0
        val e = ChestEngine(countGate = { _, _ -> calls++; false })
        e.cycle()
        assertEquals(1, calls)
    }

    @Test
    fun resetClearsPending() {
        val e = ChestEngine(countGate = { _, _ -> false })
        e.cycle()
        assertEquals(1, e.pendingCount)
        e.reset()
        assertEquals(0, e.pendingCount)
        assertEquals(0, e.expiredPending)
        assertEquals(0, e.gatedCounts)
    }
}
