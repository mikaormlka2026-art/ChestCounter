package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.track.KalmanBoxFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KalmanBoxFilterTest {
    private fun box(x: Float) = BoundingBox.fromSize(x, 100f, 50f, 50f)

    private fun movingFilter(): KalmanBoxFilter {
        val k = KalmanBoxFilter(box(100f), 0L)
        for (i in 1..10) k.update(box(100f + 30f * i), i * 100L)
        return k
    }

    @Test
    fun staticBoxHasNoVelocity() {
        val k = KalmanBoxFilter(box(100f), 0L)
        for (i in 1..7) k.update(box(100f), i * 100L)
        assertEquals(0f, k.vx, 0.01f)
        assertEquals(0f, k.vy, 0.01f)
        assertEquals(100f, k.box().left, 0.01f)
        assertEquals(150f, k.box().right, 0.01f)
    }

    @Test
    fun constantVelocityIsLearned() {
        val k = movingFilter()
        assertEquals(300f, k.vx, 5f)
        assertEquals(0f, k.vy, 1f)
    }

    @Test
    fun predictionExtrapolatesPosition() {
        val k = movingFilter()
        // dernière mesure à t = 1000 ms ; position vraie à 1100 ms : 100 + 30 * 11 = 430
        assertEquals(430f, k.predictedBox(1100L, 600L).left, 3f)
        assertEquals(490f, k.predictedBox(1300L, 600L).left, 3f)
    }

    @Test
    fun predictionHorizonIsCapped() {
        val k = movingFilter()
        val far = k.predictedBox(6000L, 600L).left
        val capped = k.predictedBox(1600L, 600L).left
        assertEquals(capped, far, 0.01f)
        assertEquals(580f, far, 3f)
    }

    @Test
    fun predictionDoesNotChangeTheFilter() {
        val k = movingFilter()
        val before = k.box().left
        k.predictedBox(3000L, 600L)
        assertEquals(before, k.box().left, 0.0001f)
    }

    @Test
    fun sizesNeverFallBelowMinimum() {
        val k = KalmanBoxFilter(BoundingBox.fromSize(100f, 100f, 1f, 1f), 0L)
        k.update(BoundingBox.fromSize(100f, 100f, 1f, 1f), 100L)
        assertTrue(k.box().width >= 1.999f)
        assertTrue(k.box().height >= 1.999f)
    }

    @Test
    fun invalidBoxAndBackwardsTimeAreIgnoredOrSafe() {
        val k = KalmanBoxFilter(box(100f), 1000L)
        val n = k.updates
        k.update(BoundingBox(10f, 10f, 5f, 5f), 1100L)
        assertEquals(n, k.updates)
        k.update(box(102f), 900L)
        assertTrue(k.box().left.isFinite())
        assertEquals(n + 1, k.updates)
    }
}
