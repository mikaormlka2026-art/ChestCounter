package com.chestcounter

import com.chestcounter.config.ClassicalConfig
import com.chestcounter.detect.FeatureMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FeatureMathTest {
    private val c = ClassicalConfig()

    @Test
    fun cosineOfIdenticalVectorsIsOne() {
        val v = floatArrayOf(1f, 2f, 3f)
        assertEquals(1f, FeatureMath.cosine(v, v)!!, 0.0001f)
    }

    @Test
    fun cosineOfOrthogonalAndOppositeVectors() {
        assertEquals(0f, FeatureMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f))!!, 0.0001f)
        assertEquals(-1f, FeatureMath.cosine(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f))!!, 0.0001f)
    }

    @Test
    fun cosineRejectsInvalidInput() {
        assertNull(FeatureMath.cosine(floatArrayOf(1f, 2f), floatArrayOf(1f, 2f, 3f)))
        assertNull(FeatureMath.cosine(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)))
        assertNull(FeatureMath.cosine(FloatArray(0), FloatArray(0)))
    }

    @Test
    fun hogScoreFollowsCosine() {
        assertEquals(0f, FeatureMath.hogScore(0.5f, c), 0.0001f)
        assertEquals(0.5f, FeatureMath.hogScore(0.725f, c), 0.0001f)
        assertEquals(1f, FeatureMath.hogScore(0.95f, c), 0.0001f)
        assertEquals(0f, FeatureMath.hogScore(-0.3f, c), 0.0001f)
    }

    @Test
    fun ratioTestRejectsAmbiguousMatches() {
        assertTrue(FeatureMath.passesRatioTest(50f, 100f, 0.75f))
        assertFalse(FeatureMath.passesRatioTest(80f, 100f, 0.75f))
        assertFalse(FeatureMath.passesRatioTest(100f, 100f, 0.75f))
    }

    @Test
    fun geometricConsistencyIsInlierShare() {
        assertEquals(0.8f, FeatureMath.geometricConsistency(10, 8)!!, 0.0001f)
        assertNull(FeatureMath.geometricConsistency(10, null))
        assertNull(FeatureMath.geometricConsistency(0, 0))
    }

    @Test
    fun orbScoreIsZeroWithTooFewMatches() {
        assertEquals(0f, FeatureMath.orbScore(5, 5, c), 0.0001f)
    }

    @Test
    fun orbScoreRewardsGeometricConsistency() {
        val coherent = FeatureMath.orbScore(25, 25, c)
        val partial = FeatureMath.orbScore(25, 10, c)
        val scattered = FeatureMath.orbScore(25, 2, c)
        assertEquals(1f, coherent, 0.0001f)
        assertEquals(0.64f, partial, 0.0001f)
        assertEquals(0.2f, scattered, 0.0001f)
        assertTrue(coherent > partial && partial > scattered)
    }

    @Test
    fun orbScoreWithoutGeometricCheckIsCapped() {
        assertEquals(0.5f, FeatureMath.orbScore(25, null, c), 0.0001f)
    }
}
