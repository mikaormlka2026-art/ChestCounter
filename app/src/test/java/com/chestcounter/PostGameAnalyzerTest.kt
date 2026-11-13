package com.chestcounter

import com.chestcounter.analysis.InteractionCue
import com.chestcounter.analysis.ObsPoint
import com.chestcounter.analysis.PlayerSample
import com.chestcounter.analysis.PostGameAnalyzer
import com.chestcounter.analysis.SessionEvidence
import com.chestcounter.analysis.Verdict
import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PostGameAnalyzerTest {
    private val analyzer = PostGameAnalyzer()
    private val C = DetectionKind.CLOSED
    private val O = DetectionKind.OPEN

    private fun seq(track: Int, kind: DetectionKind, from: Long, to: Long, x: Float = 100f) =
        (from..to step 100L).map {
            ObsPoint(0, track, it, kind, BoundingBox.fromSize(x, 100f, 50f, 50f), 0.9f)
        }

    // Un coffre fermé (0..300) puis ouvert (400..800), en (100,100).
    private val oneOpening = seq(1, C, 0, 300) + seq(1, O, 400, 800)

    private fun nearPlayer(from: Long, to: Long, x: Float = 120f, conf: Float = 0.9f) =
        (from..to step 100L).map { PlayerSample(it, BoundingBox.fromSize(x, 100f, 40f, 80f), conf) }

    private val cue = listOf(InteractionCue(300L, 0.9f, "instruction de recherche"))

    private fun verdictOf(ev: SessionEvidence) = analyzer.analyze(oneOpening, ev).events.single().verdict

    @Test
    fun withoutAnyPlayerEvidenceTheAuthorIsUnknown() {
        assertEquals(Verdict.UNKNOWN_ATTRIBUTION, verdictOf(SessionEvidence()))
    }

    @Test
    fun nearPlayerWithoutCueIsProbableAtBest() {
        val ev = SessionEvidence(player = nearPlayer(0, 900), otherPlayerTimes = emptyList())
        assertEquals(Verdict.PROBABLE_USER, verdictOf(ev))
    }

    @Test
    fun nearPlayerWithCueAndNoOtherPlayerIsConfirmed() {
        val ev = SessionEvidence(player = nearPlayer(0, 900), cues = cue, otherPlayerTimes = emptyList())
        assertEquals(Verdict.CONFIRMED_USER, verdictOf(ev))
    }

    @Test
    fun missingOtherPlayerAnalysisNeverConfirms() {
        val ev = SessionEvidence(player = nearPlayer(0, 900), cues = cue, otherPlayerTimes = null)
        assertEquals(Verdict.PROBABLE_USER, verdictOf(ev))
    }

    @Test
    fun otherPlayerNearTheChestMakesTheAuthorAmbiguous() {
        val ev = SessionEvidence(player = nearPlayer(0, 900), cues = cue, otherPlayerTimes = listOf(350L))
        assertEquals(Verdict.UNKNOWN_ATTRIBUTION, verdictOf(ev))
    }

    @Test
    fun farPlayerWithAnotherPlayerAndNoCueIsOpenedByOther() {
        val ev = SessionEvidence(player = nearPlayer(0, 900, x = 1500f), otherPlayerTimes = listOf(350L))
        assertEquals(Verdict.OPENED_BY_OTHER, verdictOf(ev))
    }

    @Test
    fun farPlayerAloneIsUnknownNotOtherNotUser() {
        val ev = SessionEvidence(player = nearPlayer(0, 900, x = 1500f), otherPlayerTimes = emptyList())
        assertEquals(Verdict.UNKNOWN_ATTRIBUTION, verdictOf(ev))
    }

    @Test
    fun unidentifiablePlayerIsUnknown() {
        val ev = SessionEvidence(player = nearPlayer(0, 900, conf = 0.2f), cues = cue, otherPlayerTimes = emptyList())
        assertEquals(Verdict.UNKNOWN_ATTRIBUTION, verdictOf(ev))
    }

    @Test
    fun playerSamplesOutsideTheWindowAreIgnored() {
        val ev = SessionEvidence(player = nearPlayer(20_000, 21_000), cues = cue, otherPlayerTimes = emptyList())
        assertEquals(Verdict.UNKNOWN_ATTRIBUTION, verdictOf(ev))
    }

    @Test
    fun reportKeepsCategoriesApartAndGivesARange() {
        val chestB = seq(2, C, 10_000, 10_300, x = 1500f) + seq(2, O, 10_400, 10_800, x = 1500f)
        val alreadyOpen = seq(3, O, 0, 800, x = 700f)
        val player = nearPlayer(0, 900) + nearPlayer(8_400, 10_900, x = 100f)
        val ev = SessionEvidence(player = player, cues = cue, otherPlayerTimes = emptyList())

        val rep = analyzer.analyze(oneOpening + chestB + alreadyOpen, ev)
        assertEquals(1, rep.confirmed)
        assertEquals(0, rep.probable)
        assertEquals(1, rep.unknown)
        assertEquals(1, rep.alreadyOpen)
        assertEquals(1, rep.minimum)
        assertEquals(2, rep.maximum)
        assertEquals(1, rep.bestEstimate)
        assertEquals(1.0f, rep.coverage!!, 0.0001f)
        assertTrue(rep.uncertaintyCauses.any { it.contains("auteur inconnu") })
    }

    @Test
    fun withoutPlayerModuleNothingIsConfirmedAndCausesAreListed() {
        val rep = analyzer.analyze(oneOpening)
        assertEquals(0, rep.confirmed)
        assertEquals(0, rep.minimum)
        assertEquals(1, rep.maximum)
        assertTrue(rep.uncertaintyCauses.any { it.contains("joueur indisponible") })
    }

    @Test
    fun noOpeningMeansNoCoverage() {
        assertNull(analyzer.analyze(seq(1, C, 0, 1000)).coverage)
    }

    @Test
    fun incompleteSessionIsFlagged() {
        val rep = analyzer.analyze(oneOpening, sessionComplete = false, incompleteReasons = listOf("Limite de stockage"))
        assertEquals(false, rep.sessionComplete)
        assertTrue(rep.uncertaintyCauses.any { it.contains("session incomplète") })
    }

    @Test
    fun analysingTwiceGivesTheSameReport() {
        val ev = SessionEvidence(player = nearPlayer(0, 900), cues = cue, otherPlayerTimes = emptyList())
        assertEquals(analyzer.analyze(oneOpening, ev), analyzer.analyze(oneOpening, ev))
    }
}
