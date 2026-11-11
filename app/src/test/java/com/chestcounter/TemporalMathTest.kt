package com.chestcounter

import com.chestcounter.config.ClassicalConfig
import com.chestcounter.detect.Ssim
import com.chestcounter.detect.TemporalHistory
import com.chestcounter.detect.TemporalMath
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TemporalMathTest {
    private val c = ClassicalConfig()

    private fun pattern(n: Int) = ByteArray(n) { ((it * 37) % 256).toByte() }

    // ---------- SSIM ----------

    @Test
    fun ssimOfIdenticalImagesIsOne() {
        val a = pattern(16 * 16)
        assertEquals(1f, Ssim.compute(a, a, 16, 16)!!, 0.0001f)
    }

    @Test
    fun ssimStaysHighForSmallBrightnessChange() {
        val a = pattern(16 * 16)
        val b = ByteArray(a.size) { ((a[it].toInt() and 0xFF) + 3).coerceAtMost(255).toByte() }
        assertTrue(Ssim.compute(a, b, 16, 16)!! > 0.99f)
    }

    @Test
    fun ssimDropsForInvertedImage() {
        val a = pattern(16 * 16)
        val inv = ByteArray(a.size) { (255 - (a[it].toInt() and 0xFF)).toByte() }
        assertTrue(Ssim.compute(a, inv, 16, 16)!! < 0.0f)
    }

    @Test
    fun ssimNeedsEnoughPixels() {
        val a = pattern(16)
        assertNull(Ssim.compute(a, a, 4, 4))
        assertNull(Ssim.compute(ByteArray(10), ByteArray(10), 16, 16))
    }

    // ---------- Notes ----------

    @Test
    fun changeScoreFollowsChangedRatio() {
        assertEquals(0f, TemporalMath.changeScore(0.01f, c), 0.0001f)
        assertEquals(0.5f, TemporalMath.changeScore(0.135f, c), 0.0001f)
        assertEquals(1f, TemporalMath.changeScore(0.25f, c), 0.0001f)
    }

    @Test
    fun structuralChangeIsInverseOfSsim() {
        assertEquals(0f, TemporalMath.structuralChangeScore(0.95f, c), 0.0001f)
        assertEquals(0.5f, TemporalMath.structuralChangeScore(0.70f, c), 0.0001f)
        assertEquals(1f, TemporalMath.structuralChangeScore(0.50f, c), 0.0001f)
        assertEquals(1f, TemporalMath.structuralChangeScore(-0.5f, c), 0.0001f)
    }

    @Test
    fun motionScoreGrowsWithResidual() {
        assertEquals(0f, TemporalMath.motionScore(0f, c), 0.0001f)
        assertEquals(0.5f, TemporalMath.motionScore(1.5f, c), 0.0001f)
        assertEquals(1f, TemporalMath.motionScore(5f, c), 0.0001f)
    }

    @Test
    fun flowQualityNeedsManyWellTrackedPoints() {
        assertEquals(0f, TemporalMath.flowQuality(0, 12, c), 0.0001f)
        assertEquals(0f, TemporalMath.flowQuality(5, 0, c), 0.0001f)
        assertEquals(1f, TemporalMath.flowQuality(12, 12, c), 0.0001f)
        assertEquals(0.1667f, TemporalMath.flowQuality(6, 12, c), 0.001f)
    }

    @Test
    fun largeTranslationIsNotReliable() {
        assertTrue(TemporalMath.translationReliable(3f, 4f, c))
        assertTrue(!TemporalMath.translationReliable(10f, 10f, c))
    }

    // ---------- Flux : résumé ----------

    @Test
    fun percentileOfSimpleList() {
        val v = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        assertEquals(3f, TemporalMath.percentile(v, 5, 0.5f), 0.0001f)
        assertEquals(4f, TemporalMath.percentile(v, 5, 0.75f), 0.0001f)
        assertEquals(0f, TemporalMath.percentile(v, 0, 0.5f), 0.0001f)
    }

    @Test
    fun summarizeSeparatesGlobalMotionFromLocalMotion() {
        val dx = floatArrayOf(5f, 5f, 5f, 5f, 15f)
        val dy = floatArrayOf(-2f, -2f, -2f, -2f, -2f)
        val s = TemporalMath.summarize(dx, dy, 5)!!
        assertEquals(5f, s.shiftX, 0.0001f)
        assertEquals(-2f, s.shiftY, 0.0001f)
        assertEquals(0f, s.residualP75, 0.0001f)
    }

    @Test
    fun summarizeSeesLocalMotionWhenMostPointsMove() {
        val dx = floatArrayOf(0f, 0f, 6f, 6f, 6f, 6f)
        val dy = FloatArray(6)
        val s = TemporalMath.summarize(dx, dy, 6)!!
        assertTrue(s.residualP75 > 2f)
    }

    @Test
    fun summarizeWithoutPointsIsNull() {
        assertNull(TemporalMath.summarize(FloatArray(0), FloatArray(0), 0))
    }

    // ---------- Recalage et différence ----------

    @Test
    fun alignOverlapShiftsPreviousImage() {
        val prev = byteArrayOf(10, 20, 30, 40)
        val curr = byteArrayOf(11, 21, 31, 41)
        val outPrev = ByteArray(4)
        val outCurr = ByteArray(4)
        val ov = TemporalMath.alignOverlap(prev, curr, 4, 1, 1, 0, outPrev, outCurr)
        assertNotNull(ov)
        assertEquals(3, ov!!.width)
        assertEquals(1, ov.height)
        assertArrayEquals(byteArrayOf(10, 20, 30), outPrev.copyOf(3))
        assertArrayEquals(byteArrayOf(21, 31, 41), outCurr.copyOf(3))
    }

    @Test
    fun alignOverlapWithoutShiftCopiesEverything() {
        val prev = byteArrayOf(1, 2, 3, 4)
        val curr = byteArrayOf(5, 6, 7, 8)
        val outPrev = ByteArray(4)
        val outCurr = ByteArray(4)
        val ov = TemporalMath.alignOverlap(prev, curr, 2, 2, 0, 0, outPrev, outCurr)!!
        assertEquals(2, ov.width)
        assertEquals(2, ov.height)
        assertArrayEquals(prev, outPrev)
        assertArrayEquals(curr, outCurr)
    }

    @Test
    fun alignOverlapRejectsTooLittleCommonArea() {
        val a = ByteArray(4)
        assertNull(TemporalMath.alignOverlap(a, a, 4, 1, 3, 0, ByteArray(4), ByteArray(4)))
    }

    @Test
    fun changedRatioCountsPixelsAboveThreshold() {
        val a = byteArrayOf(0, 0, 0, 0)
        val b = byteArrayOf(0, 30, 0, 100)
        assertEquals(0.5f, TemporalMath.changedRatio(a, b, 4, 25), 0.0001f)
        assertEquals(0.5f, TemporalMath.changedRatio(byteArrayOf(0, 0), byteArrayOf(0, 200.toByte()), 2, 25), 0.0001f)
        assertEquals(0f, TemporalMath.changedRatio(a, a, 4, 25), 0.0001f)
    }

    // ---------- Historique ----------

    @Test
    fun historyReturnsRecentSnapshotAndForgetsOldOnes() {
        val h = TemporalHistory(maxGapMs = 1000, maxTracks = 4)
        h.put(1, 100L, 2, 2, byteArrayOf(1, 2, 3, 4))
        assertNotNull(h.get(1, 600L))
        assertNull(h.get(1, 1200L))
        assertNull(h.get(1, 50L))
        assertNull(h.get(2, 600L))
    }

    @Test
    fun historyKeepsLatestObservationOnly() {
        val h = TemporalHistory(maxGapMs = 1000)
        h.put(1, 100L, 2, 2, byteArrayOf(1, 2, 3, 4))
        h.put(1, 200L, 2, 2, byteArrayOf(9, 9, 9, 9))
        val s = h.get(1, 300L)!!
        assertEquals(200L, s.tMs)
        assertArrayEquals(byteArrayOf(9, 9, 9, 9), s.data)
        assertEquals(1, h.size())
    }

    @Test
    fun historyIsLimitedAndPrunable() {
        val h = TemporalHistory(maxGapMs = 1000, maxTracks = 2)
        h.put(1, 0L, 2, 2, ByteArray(4))
        h.put(2, 0L, 2, 2, ByteArray(4))
        h.put(3, 0L, 2, 2, ByteArray(4))
        assertEquals(2, h.size())
        assertNull(h.get(1, 10L))
        h.prune(5000L)
        assertEquals(0, h.size())
    }
}
