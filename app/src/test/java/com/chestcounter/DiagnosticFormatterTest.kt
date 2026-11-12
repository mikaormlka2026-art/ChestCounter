package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.CandidateStatus
import com.chestcounter.detect.DetectionKind
import com.chestcounter.detect.FusionOutcome
import com.chestcounter.detect.RoiScores
import com.chestcounter.diagnostic.CandidateReport
import com.chestcounter.diagnostic.DiagnosticFormatter
import com.chestcounter.diagnostic.PipelineLog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticFormatterTest {

    private fun report(
        roi: RoiScores? = null,
        outcome: FusionOutcome? = null,
        vetoed: Boolean = false,
        trackId: Int? = 3
    ) = CandidateReport(
        trackId = trackId,
        kind = DetectionKind.CLOSED,
        box = BoundingBox.fromSize(100f, 120f, 60f, 50f),
        templateScore = 0.9f,
        roi = roi,
        temporal = null,
        outcome = outcome,
        vetoed = vetoed,
        state = "DETECTED",
        velocityX = 12.4f,
        velocityY = -3.6f
    )

    @Test
    fun percentFormatting() {
        assertEquals("—", DiagnosticFormatter.pct(null))
        assertEquals("0%", DiagnosticFormatter.pct(-0.3f))
        assertEquals("100%", DiagnosticFormatter.pct(2f))
        assertEquals("85%", DiagnosticFormatter.pct(0.85f))
    }

    @Test
    fun headerShowsTrackKindAndVerdict() {
        val o = FusionOutcome(0.84f, CandidateStatus.CONFIRMED, emptyList(), 3, emptyList())
        val lines = DiagnosticFormatter.lines(report(outcome = o))
        assertTrue(lines[0].contains("#3"))
        assertTrue(lines[0].contains("FERMÉ"))
        assertTrue(lines[0].contains("CONFIRMED"))
        assertTrue(lines[0].contains("84%"))
    }

    @Test
    fun unavailableScoresShowADash() {
        val lines = DiagnosticFormatter.lines(report())
        assertTrue(lines[0].contains("non analysé"))
        assertTrue(lines[1].contains("Couleur —"))
        assertTrue(lines[2].contains("ORB —"))
    }

    @Test
    fun availableScoresAreShown() {
        val roi = RoiScores(
            hsvScore = 0.7f, edgeScore = 0.8f, contourScore = 0.6f, geometryScore = 0.5f,
            shapeScore = 0.4f, processingUs = 12_500L, hogScore = 0.9f, orbScore = 0.3f,
            orbMatchCount = 12, geometricConsistency = 0.75f
        )
        val lines = DiagnosticFormatter.lines(report(roi = roi))
        assertTrue(lines[1].contains("Couleur 70%"))
        assertTrue(lines[1].contains("Hu 40%"))
        assertTrue(lines[2].contains("HOG 90%"))
        assertTrue(lines[2].contains("12 corr."))
        assertTrue(lines[4].contains("12.5 ms"))
        assertTrue(lines[4].contains("(12, -4) px/s"))
    }

    @Test
    fun vetoedCandidateIsMarkedAndReasonsAreListed() {
        val o = FusionOutcome(0.4f, CandidateStatus.REJECTED, emptyList(), 1, listOf("4 canaux contredisent"))
        val lines = DiagnosticFormatter.lines(report(outcome = o, vetoed = true, trackId = null))
        assertTrue(lines[0].contains("REJETÉ"))
        assertTrue(lines[0].contains("#–"))
        assertTrue(lines.last().startsWith("Raisons"))
    }

    @Test
    fun reportsAreSeparated() {
        val lines = DiagnosticFormatter.format(listOf(report(), report()))
        assertEquals(1, lines.count { it.startsWith("──") })
        assertFalse(DiagnosticFormatter.format(emptyList()).any())
    }

    @Test
    fun pipelineLogFormatsOmitsNullsAndKeepsOnlyTheLastLines() {
        val sunk = ArrayList<String>()
        val log = PipelineLog(capacity = 2) { sunk.add(it) }
        val line = log.event(1000L, 7, 3, "COUNTED", 0.85f, mapOf("tpl" to 0.9f, "orb" to null))
        assertEquals("t=1000 f=7 track=3 state=COUNTED conf=0.85 tpl=0.90", line)
        log.event(1100L, 8, null, "VETOED", null)
        log.event(1200L, 9, 4, "OPENED", null)
        assertEquals(2, log.recent().size)
        assertTrue(log.recent()[0].contains("VETOED"))
        assertEquals(3, sunk.size)
        log.clear()
        assertTrue(log.recent().isEmpty())
    }
}
