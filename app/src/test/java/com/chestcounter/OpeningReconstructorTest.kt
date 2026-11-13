package com.chestcounter

import com.chestcounter.analysis.ObsPoint
import com.chestcounter.analysis.OpeningReconstructor
import com.chestcounter.analysis.ReconOutcome
import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OpeningReconstructorTest {
    private val rec = OpeningReconstructor()

    private fun o(track: Int, t: Long, kind: DetectionKind, x: Float = 100f, epoch: Int = 0) =
        ObsPoint(epoch, track, t, kind, BoundingBox.fromSize(x, 100f, 50f, 50f), 0.9f)

    private fun seq(track: Int, kind: DetectionKind, from: Long, to: Long, x: Float = 100f, epoch: Int = 0) =
        (from..to step 100L).map { o(track, it, kind, x, epoch) }

    private val C = DetectionKind.CLOSED
    private val O = DetectionKind.OPEN

    @Test
    fun closedThenOpenIsAnObservedOpening() {
        val r = rec.reconstruct(seq(1, C, 0, 300) + seq(1, O, 400, 800))
        assertEquals(1, r.size)
        assertEquals(ReconOutcome.OPENED, r[0].outcome)
        assertEquals(300L, r[0].lastClosedMs)
        assertEquals(400L, r[0].firstOpenMs)
    }

    @Test
    fun alreadyOpenOnFirstObservationIsNotAnOpening() {
        val r = rec.reconstruct(seq(1, O, 0, 800))
        assertEquals(ReconOutcome.ALREADY_OPEN_ON_FIRST_OBSERVATION, r[0].outcome)
    }

    @Test
    fun closedOnlyIsNotOpened() {
        val r = rec.reconstruct(seq(1, C, 0, 1000))
        assertEquals(ReconOutcome.NOT_OPENED, r[0].outcome)
    }

    @Test
    fun tooFewClosedObservationsIsInvalid() {
        val r = rec.reconstruct(seq(1, C, 0, 100) + seq(1, O, 200, 700))
        assertEquals(ReconOutcome.INVALID, r[0].outcome)
        assertTrue(r[0].reason.contains("fermé"))
    }

    @Test
    fun momentaryOpenStateIsInvalid() {
        val r = rec.reconstruct(seq(1, C, 0, 300) + listOf(o(1, 400, O)) + seq(1, C, 500, 800))
        assertEquals(ReconOutcome.INVALID, r[0].outcome)
    }

    @Test
    fun bigGapBetweenClosedAndOpenIsInvalid() {
        val r = rec.reconstruct(seq(1, C, 0, 300) + seq(1, O, 2500, 3000))
        assertEquals(ReconOutcome.INVALID, r[0].outcome)
        assertTrue(r[0].reason.contains("lacune"))
    }

    @Test
    fun returningToClosedAfterOpeningIsInvalid() {
        val r = rec.reconstruct(seq(1, C, 0, 300) + seq(1, O, 400, 700) + seq(1, C, 800, 1100))
        assertEquals(ReconOutcome.INVALID, r[0].outcome)
    }

    @Test
    fun sameSpotOpenedTwiceByTwoTracksIsMerged() {
        val obs = seq(1, C, 0, 300) + seq(1, O, 400, 800) +
            seq(2, C, 1000, 1300) + seq(2, O, 1400, 1800)
        val r = rec.reconstruct(obs)
        assertEquals(1, r.count { it.outcome == ReconOutcome.OPENED })
        assertEquals(1, r.count { it.outcome == ReconOutcome.INVALID && it.reason.contains("doublon") })
    }

    @Test
    fun twoDistinctChestsAreBothKept() {
        val obs = seq(1, C, 0, 300, x = 100f) + seq(1, O, 400, 800, x = 100f) +
            seq(2, C, 1000, 1300, x = 600f) + seq(2, O, 1400, 1800, x = 600f)
        assertEquals(2, rec.reconstruct(obs).count { it.outcome == ReconOutcome.OPENED })
    }

    @Test
    fun sameTrackIdInAnotherEpochIsADifferentChest() {
        val obs = seq(1, C, 0, 300, epoch = 0) + seq(1, O, 400, 800, epoch = 0) +
            seq(1, C, 5000, 5500, x = 600f, epoch = 1)
        val r = rec.reconstruct(obs)
        assertEquals(1, r.count { it.outcome == ReconOutcome.OPENED })
        assertEquals(1, r.count { it.outcome == ReconOutcome.NOT_OPENED })
    }

    @Test
    fun resultDoesNotDependOnInputOrder() {
        val obs = seq(1, C, 0, 300) + seq(1, O, 400, 800)
        assertEquals(rec.reconstruct(obs), rec.reconstruct(obs.reversed()))
    }
}
