package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.detect.nonMaxSuppression
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BoundingBoxTest {

    private fun det(kind: DetectionKind, x: Float, score: Float) =
        Detection(kind, BoundingBox.fromSize(x, 0f, 10f, 10f), score)

    @Test
    fun dimensions_et_centre() {
        val b = BoundingBox(10f, 20f, 40f, 60f)
        assertEquals(30f, b.width, 0.0001f)
        assertEquals(40f, b.height, 0.0001f)
        assertEquals(1200f, b.area, 0.0001f)
        assertEquals(25f, b.centerX, 0.0001f)
        assertEquals(40f, b.centerY, 0.0001f)
    }

    @Test
    fun boite_invalide_a_une_aire_nulle() {
        val b = BoundingBox(10f, 10f, 5f, 20f)
        assertFalse(b.isValid())
        assertEquals(0f, b.area, 0.0001f)
    }

    @Test
    fun iou_identique_vaut_un() {
        val b = BoundingBox(0f, 0f, 10f, 10f)
        assertEquals(1f, b.iou(b), 0.0001f)
    }

    @Test
    fun iou_disjoint_vaut_zero() {
        val a = BoundingBox(0f, 0f, 10f, 10f)
        val b = BoundingBox(20f, 20f, 30f, 30f)
        assertEquals(0f, a.iou(b), 0.0001f)
    }

    @Test
    fun iou_partiel() {
        val a = BoundingBox(0f, 0f, 10f, 10f)
        val b = BoundingBox(5f, 0f, 15f, 10f)
        assertEquals(1f / 3f, a.iou(b), 0.0001f)
    }

    @Test
    fun from_size_et_scaled() {
        val b = BoundingBox.fromSize(2f, 4f, 6f, 8f).scaled(2f)
        assertEquals(BoundingBox(4f, 8f, 16f, 24f), b)
    }

    @Test
    fun normalized_reste_dans_zero_un() {
        val b = BoundingBox(100f, 50f, 200f, 100f).normalized(400f, 200f)
        assertEquals(0.25f, b.left, 0.0001f)
        assertEquals(0.25f, b.top, 0.0001f)
        assertEquals(0.5f, b.right, 0.0001f)
        assertEquals(0.5f, b.bottom, 0.0001f)
    }

    @Test
    fun nms_garde_le_meilleur_des_doublons() {
        val kept = nonMaxSuppression(
            listOf(
                det(DetectionKind.CLOSED, 0f, 0.8f),
                det(DetectionKind.CLOSED, 1f, 0.9f)
            ),
            0.4f,
            8
        )
        assertEquals(1, kept.size)
        assertEquals(0.9f, kept[0].score, 0.0001f)
    }

    @Test
    fun nms_ne_fusionne_pas_des_types_differents() {
        val kept = nonMaxSuppression(
            listOf(
                det(DetectionKind.CLOSED, 0f, 0.8f),
                det(DetectionKind.OPEN, 0f, 0.7f)
            ),
            0.4f,
            8
        )
        assertEquals(2, kept.size)
        assertTrue(kept.any { it.kind == DetectionKind.OPEN })
    }

    @Test
    fun nms_limite_le_nombre_et_garde_les_meilleurs() {
        val items = (0 until 5).map { det(DetectionKind.CLOSED, it * 20f, 0.5f + it * 0.1f) }
        val kept = nonMaxSuppression(items, 0.4f, 3)
        assertEquals(3, kept.size)
        assertEquals(0.9f, kept[0].score, 0.0001f)
        assertEquals(0.7f, kept[2].score, 0.0001f)
    }

    @Test
    fun nms_liste_vide() {
        assertTrue(nonMaxSuppression(emptyList(), 0.4f, 8).isEmpty())
    }
}
