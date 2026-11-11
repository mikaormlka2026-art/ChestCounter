package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.BoundingBoxRefiner
import com.chestcounter.detect.ScoredBox
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundingBoxRefinerTest {

    private val refiner = BoundingBoxRefiner()

    private fun box(l: Float, t: Float, r: Float, b: Float, s: Float) =
        ScoredBox(BoundingBox(l, t, r, b), s)

    @Test
    fun duplicatesAreMergedIntoOneBox() {
        val out = refiner.refine(
            listOf(
                box(100f, 100f, 200f, 200f, 0.9f),
                box(104f, 102f, 204f, 202f, 0.6f)
            ),
            1280, 720
        )
        assertEquals(1, out.size)
        assertEquals(0.9f, out[0].score, 0.0001f)
        assertTrue(out[0].box.left >= 100f && out[0].box.left <= 104f)
    }

    @Test
    fun distantBoxesStaySeparate() {
        val out = refiner.refine(
            listOf(
                box(100f, 100f, 200f, 200f, 0.9f),
                box(600f, 300f, 700f, 400f, 0.8f)
            ),
            1280, 720
        )
        assertEquals(2, out.size)
    }

    @Test
    fun resultIsSortedByScore() {
        val out = refiner.refine(
            listOf(
                box(600f, 300f, 700f, 400f, 0.5f),
                box(100f, 100f, 200f, 200f, 0.9f)
            ),
            1280, 720
        )
        assertEquals(0.9f, out[0].score, 0.0001f)
        assertEquals(0.5f, out[1].score, 0.0001f)
    }

    @Test
    fun boxIsClampedToFrame() {
        val out = refiner.refine(listOf(box(-20f, 10f, 80f, 110f, 0.8f)), 1280, 720)
        assertEquals(1, out.size)
        assertEquals(0f, out[0].box.left, 0.0001f)
    }

    @Test
    fun boxOutsideFrameIsDropped() {
        val out = refiner.refine(listOf(box(1300f, 10f, 1400f, 110f, 0.8f)), 1280, 720)
        assertTrue(out.isEmpty())
    }

    @Test
    fun tinyBoxIsRejected() {
        val out = refiner.refine(listOf(box(10f, 10f, 14f, 14f, 0.9f)), 1280, 720)
        assertTrue(out.isEmpty())
    }

    @Test
    fun extremeAspectRatioIsRejected() {
        val out = refiner.refine(listOf(box(10f, 10f, 400f, 40f, 0.9f)), 1280, 720)
        assertTrue(out.isEmpty())
    }

    @Test
    fun invalidFrameSizeGivesNoBox() {
        val out = refiner.refine(listOf(box(10f, 10f, 100f, 100f, 0.9f)), 0, 0)
        assertTrue(out.isEmpty())
    }
}
