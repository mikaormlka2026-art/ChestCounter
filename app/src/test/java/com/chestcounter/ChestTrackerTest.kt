package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.track.ChestTracker
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChestTrackerTest {

    private fun det(
        kind: DetectionKind,
        x: Float,
        y: Float,
        w: Float = 50f,
        h: Float = 50f,
        score: Float = 0.9f
    ) = Detection(kind, BoundingBox.fromSize(x, y, w, h), score)

    @Test
    fun nouveau_coffre_confirme_apres_deux_images() {
        val t = ChestTracker()
        val first = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        assertTrue(first.tracks.isEmpty())
        val second = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 100L)
        assertEquals(1, second.tracks.size)
    }

    @Test
    fun identifiant_stable_entre_images() {
        val t = ChestTracker()
        var id = -1
        for (i in 0 until 6) {
            val u = t.update(
                listOf(det(DetectionKind.CLOSED, 100f + i * 2f, 100f)),
                i * 100L
            )
            if (u.tracks.isNotEmpty()) {
                if (id < 0) id = u.tracks[0].id
                assertEquals(id, u.tracks[0].id)
            }
        }
        assertTrue(id > 0)
    }

    @Test
    fun ferme_puis_ouvert_garde_le_meme_identifiant() {
        val t = ChestTracker()
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        val closed = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 100L)
        val id = closed.tracks[0].id
        val open = t.update(
            listOf(det(DetectionKind.OPEN, 75f, 75f, 100f, 100f)),
            200L
        )
        assertEquals(1, open.tracks.size)
        assertEquals(id, open.tracks[0].id)
        assertEquals(DetectionKind.OPEN, open.tracks[0].kind)
    }

    @Test
    fun deux_coffres_deux_identifiants() {
        val t = ChestTracker()
        val both = listOf(
            det(DetectionKind.CLOSED, 50f, 50f),
            det(DetectionKind.CLOSED, 400f, 300f)
        )
        t.update(both, 0L)
        val u = t.update(both, 100L)
        assertEquals(2, u.tracks.size)
        assertNotEquals(u.tracks[0].id, u.tracks[1].id)
    }

    @Test
    fun une_detection_reste_une_seule_piste() {
        val t = ChestTracker()
        var last = 0
        for (i in 0 until 10) {
            last = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), i * 100L).tracks.size
        }
        assertEquals(1, last)
    }

    @Test
    fun perte_courte_conserve_identifiant() {
        val t = ChestTracker()
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        val id = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 100L).tracks[0].id

        val hidden = t.update(emptyList(), 200L)
        assertEquals(1, hidden.tracks.size)
        assertFalse(hidden.tracks[0].visible)

        val back = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 900L)
        assertEquals(id, back.tracks[0].id)
        assertTrue(back.tracks[0].visible)
    }

    @Test
    fun perte_longue_supprime_et_signale() {
        val t = ChestTracker()
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        val id = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 100L).tracks[0].id
        val u = t.update(emptyList(), 1700L)
        assertTrue(u.tracks.isEmpty())
        assertEquals(listOf(id), u.lostIds)
    }

    @Test
    fun piste_non_confirmee_perdue_nest_pas_signalee() {
        val t = ChestTracker()
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        val u = t.update(emptyList(), 2000L)
        assertTrue(u.lostIds.isEmpty())
    }

    @Test
    fun identifiants_non_reutilises() {
        val t = ChestTracker()
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        val first = t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 100L).tracks[0].id
        t.update(emptyList(), 2000L)
        t.update(listOf(det(DetectionKind.CLOSED, 500f, 400f)), 2100L)
        val second = t.update(listOf(det(DetectionKind.CLOSED, 500f, 400f)), 2200L).tracks[0].id
        assertNotEquals(first, second)
    }

    @Test
    fun boite_lissee_entre_ancienne_et_nouvelle_position() {
        val t = ChestTracker()
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        val u = t.update(listOf(det(DetectionKind.CLOSED, 120f, 100f)), 100L)
        assertEquals(110f, u.tracks[0].box.left, 0.001f)
    }

    @Test
    fun reset_vide_tout() {
        val t = ChestTracker()
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 0L)
        t.update(listOf(det(DetectionKind.CLOSED, 100f, 100f)), 100L)
        t.reset()
        val u = t.update(emptyList(), 200L)
        assertTrue(u.tracks.isEmpty())
    }
}
