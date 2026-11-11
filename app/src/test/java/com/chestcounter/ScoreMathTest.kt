package com.chestcounter

import com.chestcounter.config.ClassicalConfig
import com.chestcounter.detect.ScoreMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreMathTest {
    private val c = ClassicalConfig()

    @Test
    fun rampIsClamped() {
        assertEquals(0f, ScoreMath.ramp(-1f, 0f, 1f), 0.0001f)
        assertEquals(0.5f, ScoreMath.ramp(0.5f, 0f, 1f), 0.0001f)
        assertEquals(1f, ScoreMath.ramp(3f, 0f, 1f), 0.0001f)
    }

    @Test
    fun edgeScoreIsZeroWhenTooEmptyOrTooBusy() {
        assertEquals(0f, ScoreMath.edgeScore(0f, c), 0.0001f)
        assertEquals(1f, ScoreMath.edgeScore(0.15f, c), 0.0001f)
        assertEquals(0.5f, ScoreMath.edgeScore(0.05f, c), 0.0001f)
        assertEquals(0f, ScoreMath.edgeScore(0.8f, c), 0.0001f)
    }

    @Test
    fun continuityDropsWithFragments() {
        assertEquals(0f, ScoreMath.continuity(0, 0), 0.0001f)
        assertEquals(1f, ScoreMath.continuity(1, 0), 0.0001f)
        assertEquals(0.5f, ScoreMath.continuity(1, 4), 0.0001f)
    }

    @Test
    fun contourScoreNeedsCoverage() {
        assertEquals(1f, ScoreMath.contourScore(0.6f, 1f, c), 0.0001f)
        assertEquals(0.4f, ScoreMath.contourScore(0.05f, 1f, c), 0.0001f)
    }

    @Test
    fun geometryAcceptsChestLikeBoxAndRejectsStrips() {
        assertEquals(1f, ScoreMath.geometryScore(1.3f, 0.85f, 0.95f, c), 0.0001f)
        assertEquals(0f, ScoreMath.geometryScore(6f, 0.2f, 0.3f, c), 0.0001f)
    }

    @Test
    fun flatYellowZoneIsNotAChest() {
        val flat = ScoreMath.hsvScore(0.95f, 9, 9, 0f, c)
        assertTrue(flat < 0.1f)
    }

    @Test
    fun goldWithDarkOutlinesScoresHigherThanPlainGoldBlob() {
        val chestLike = ScoreMath.hsvScore(0.35f, 7, 9, 0.2f, c)
        val blob = ScoreMath.hsvScore(0.35f, 6, 9, 0f, c)
        assertTrue(chestLike > 0.9f)
        assertTrue(blob < chestLike)
    }

    @Test
    fun hsvScoreHandlesEmptyGrid() {
        assertEquals(0f, ScoreMath.hsvScore(0.3f, 0, 0, 0.2f, c), 0.0001f)
    }
}
