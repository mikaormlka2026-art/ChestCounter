package com.chestcounter

import com.chestcounter.config.DetectionConfig
import com.chestcounter.config.ProfileName
import com.chestcounter.detect.CandidateEvidence
import com.chestcounter.detect.CandidateStatus
import com.chestcounter.detect.EvidenceChannel
import com.chestcounter.detect.EvidenceFusionEngine
import com.chestcounter.detect.RoiScores
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EvidenceFusionTest {
    private val engine = EvidenceFusionEngine()

    private fun roi(
        hsv: Float? = null, edge: Float? = null, contour: Float? = null, geometry: Float? = null,
        shape: Float? = null, hog: Float? = null, orb: Float? = null
    ) = RoiScores(
        hsvScore = hsv, edgeScore = edge, contourScore = contour, geometryScore = geometry,
        shapeScore = shape, processingUs = 0L, hogScore = hog, orbScore = orb
    )

    @Test
    fun allEvidenceAgreesConfirms() {
        val r = roi(hsv = 0.7f, edge = 0.8f, contour = 0.8f, geometry = 0.8f, shape = 0.8f, hog = 0.8f, orb = 0.8f)
        val o = engine.evaluate(CandidateEvidence(0.9f, r, 0.9f))
        assertEquals(CandidateStatus.CONFIRMED, o.status)
        assertTrue(o.confidence > 0.8f)
        assertTrue(o.supportCount >= 2)
    }

    @Test
    fun templateAloneCannotConfirm() {
        val o = engine.evaluate(CandidateEvidence(0.9f))
        assertEquals(CandidateStatus.PROBABLE, o.status)
        assertTrue(o.reasons.isNotEmpty())
    }

    @Test
    fun templatePlusOneIndependentEvidenceCanConfirm() {
        val o = engine.evaluate(CandidateEvidence(0.8f, roi(orb = 0.8f)))
        assertEquals(CandidateStatus.CONFIRMED, o.status)
        assertEquals(2, o.supportCount)
    }

    @Test
    fun goldenFakeObjectIsRejected() {
        val r = roi(hsv = 0.8f, edge = 0.05f, contour = 0.05f, geometry = 0.05f, shape = 0.05f, hog = 0.05f, orb = 0f)
        val o = engine.evaluate(CandidateEvidence(0.72f, r))
        assertEquals(CandidateStatus.REJECTED, o.status)
        assertTrue(o.reasons.any { it.contains("contredisent") })
    }

    @Test
    fun strongColorAloneIsNeverEnough() {
        val o = engine.evaluate(CandidateEvidence(0.2f, roi(hsv = 1.0f)))
        assertEquals(CandidateStatus.REJECTED, o.status)
        assertEquals(0, o.supportCount)
    }

    @Test
    fun confirmationNeedsEnoughSupportingChannels() {
        val o = engine.evaluate(CandidateEvidence(1.0f, roi(orb = 0.25f)))
        assertEquals(CandidateStatus.PROBABLE, o.status)
        assertTrue(o.reasons.any { it.contains("support") })
    }

    @Test
    fun profilesChangeTheStrictness() {
        val ev = CandidateEvidence(0.8f, roi(orb = 0.8f))
        val strict = EvidenceFusionEngine(DetectionConfig.forProfile(ProfileName.STRICT)).evaluate(ev)
        val balanced = EvidenceFusionEngine(DetectionConfig.forProfile(ProfileName.BALANCED)).evaluate(ev)
        val permissive = EvidenceFusionEngine(DetectionConfig.forProfile(ProfileName.PERMISSIVE)).evaluate(ev)
        assertEquals(CandidateStatus.PROBABLE, strict.status)
        assertEquals(CandidateStatus.CONFIRMED, balanced.status)
        assertEquals(CandidateStatus.CONFIRMED, permissive.status)
    }

    @Test
    fun unavailableMethodsAreIgnoredNotCountedAsZero() {
        val o = engine.evaluate(CandidateEvidence(0.8f, roi(hsv = 0.8f)))
        assertTrue(o.channels.none { it.channel == EvidenceChannel.ORB })
        assertTrue(o.channels.none { it.channel == EvidenceChannel.HOG })
        assertEquals(0.8f, o.confidence, 0.001f)
    }

    @Test
    fun disabledMethodIsIgnoredEvenIfScored() {
        val e = EvidenceFusionEngine(DetectionConfig(orbEnabled = false))
        val o = e.evaluate(CandidateEvidence(0.8f, roi(orb = 0.0f)))
        assertTrue(o.channels.none { it.channel == EvidenceChannel.ORB })
    }

    @Test
    fun contourMeasurementsAreGroupedInOneChannel() {
        val o = engine.evaluate(CandidateEvidence(0.8f, roi(edge = 0.6f, contour = 0.6f, geometry = 0.6f)))
        assertEquals(1, o.channels.count { it.channel == EvidenceChannel.CONTOUR })
    }

    @Test
    fun scoresAreClamped() {
        val o = engine.evaluate(CandidateEvidence(1.5f))
        assertEquals(1f, o.channels[0].value, 0.0001f)
        assertEquals(1f, o.confidence, 0.0001f)
    }
}
