package com.chestcounter

import com.chestcounter.detect.HuMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Valeurs de Hu (4 premiers invariants) calculées avec OpenCV sur des formes synthétiques pleines. */
class HuMathTest {
    private val rectA = doubleArrayOf(0.1771022, 0.00359565, 1.053863e-32, 1.170959e-33) // 100x70
    private val rectScaled = doubleArrayOf(0.1768967, 0.003537143, 0.0, 0.0)            // 60x42
    private val rectFlat = doubleArrayOf(0.2970297, 0.06048049, 0.0, 0.0)               // 100x30
    private val circle = doubleArrayOf(0.1591566, 2.344922e-31, 3.826004e-31, 7.498968e-31)

    @Test
    fun identicalShapesAreSimilar() {
        assertEquals(1f, HuMath.similarity(rectA, rectA, 1.0)!!, 0.0001f)
    }

    @Test
    fun scaledShapeStaysSimilar() {
        assertTrue(HuMath.similarity(rectA, rectScaled, 1.0)!! > 0.95f)
    }

    @Test
    fun differentProportionsScoreLower() {
        val s = HuMath.similarity(rectA, rectFlat, 1.0)!!
        assertTrue(s < 0.5f)
    }

    @Test
    fun circleIsFarFromRectangle() {
        assertTrue(HuMath.similarity(rectA, circle, 1.0)!! < 0.1f)
    }

    @Test
    fun distanceIsSymmetric() {
        assertEquals(HuMath.distance(rectA, rectFlat)!!, HuMath.distance(rectFlat, rectA)!!, 1e-9)
    }

    @Test
    fun missingOrInvalidDataGivesNull() {
        assertNull(HuMath.distance(DoubleArray(0), rectA))
        assertNull(HuMath.distance(doubleArrayOf(Double.NaN, 0.1), doubleArrayOf(0.1, 0.1)))
        assertNotNull(HuMath.distance(rectA, rectScaled))
    }
}
