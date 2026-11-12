package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.track.ChestTracker
import com.chestcounter.track.TrackerConfig
import com.chestcounter.track.TrackerUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChestTrackerKalmanTest {
    // Un coffre dont le mouvement apparent accélère (caméra qui tourne), image toutes les 100 ms.
    private val xs = listOf(100f, 110f, 125f, 145f, 170f, 200f, 235f, 275f, 320f)

    private fun det(x: Float) =
        Detection(DetectionKind.CLOSED, BoundingBox.fromSize(x, 100f, 50f, 50f), 0.9f)

    private fun runAll(t: ChestTracker): TrackerUpdate {
        var last = TrackerUpdate(emptyList(), emptyList())
        xs.forEachIndexed { i, x -> last = t.update(listOf(det(x)), i * 100L) }
        return last
    }

    @Test
    fun acceleratingChestKeepsOneTrackWithKalman() {
        val u = runAll(ChestTracker(TrackerConfig(useKalman = true)))
        assertEquals(1, u.tracks.size)
        val tr = u.tracks[0]
        assertTrue(tr.visible)
        assertEquals(xs.size, tr.hits)
        assertEquals(xs.size, tr.age)
        assertEquals(0, tr.missedFrames)
        assertTrue(tr.velocityX > 300f)
    }

    @Test
    fun sameMotionLosesTheTrackWithoutKalman() {
        val u = runAll(ChestTracker(TrackerConfig(useKalman = false)))
        assertTrue(u.tracks.none { it.hits == xs.size })
    }

    @Test
    fun hiddenTrackCoastsForwardAndCountsMissedFrames() {
        val t = ChestTracker()
        val last = runAll(t).tracks[0]
        val hidden = t.update(emptyList(), 1000L)
        assertEquals(1, hidden.tracks.size)
        val h = hidden.tracks[0]
        assertFalse(h.visible)
        assertEquals(1, h.missedFrames)
        assertTrue(h.box.left > last.box.left)
    }
}
