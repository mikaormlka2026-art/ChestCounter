package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionKind
import com.chestcounter.fusion.ChestEngine
import com.chestcounter.fusion.ChestPhase
import com.chestcounter.fusion.EngineOutput
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChestEngineTest {

    private fun det(
        kind: DetectionKind,
        x: Float = 100f,
        y: Float = 100f,
        score: Float = 0.9f
    ) = Detection(kind, BoundingBox.fromSize(x, y, 50f, 50f), score)

    private fun ChestEngine.feed(
        dets: List<Detection>,
        t: Long,
        gold: Float = 0f
    ): EngineOutput = process(dets, t) { gold }

    private fun ChestEngine.run(
        kind: DetectionKind,
        from: Long,
        to: Long,
        score: Float = 0.9f,
        x: Float = 100f,
        y: Float = 100f
    ) {
        var t = from
        while (t <= to) {
            feed(listOf(det(kind, x, y, score)), t)
            t += 100L
        }
    }

    @Test
    fun ferme_puis_ouvert_compte_un_coffre() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 100L)
        e.run(DetectionKind.OPEN, 200L, 500L)
        assertEquals(1, e.count)
    }

    @Test
    fun coffre_ferme_seul_ne_compte_pas() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 2000L)
        assertEquals(0, e.count)
    }

    @Test
    fun coffre_deja_ouvert_ne_compte_pas() {
        val e = ChestEngine()
        e.run(DetectionKind.OPEN, 0L, 2000L)
        assertEquals(0, e.count)
    }

    @Test
    fun comptage_unique_meme_si_le_coffre_reste_ouvert() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 100L)
        e.run(DetectionKind.OPEN, 200L, 3000L)
        assertEquals(1, e.count)
    }

    @Test
    fun score_trop_bas_est_ignore() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 1000L, score = 0.5f)
        val out = e.feed(listOf(det(DetectionKind.CLOSED, score = 0.5f)), 1100L)
        assertTrue(out.tracks.isEmpty())
        assertEquals(0, e.count)
    }

    @Test
    fun ferme_perdu_avant_ouverture_ne_compte_pas() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 100L)
        e.feed(emptyList(), 2000L)
        assertEquals(0, e.count)
    }

    @Test
    fun conflit_ferme_et_ouvert_au_meme_endroit_garde_le_meilleur() {
        val e = ChestEngine()
        val both = listOf(
            det(DetectionKind.CLOSED, score = 0.9f),
            det(DetectionKind.OPEN, score = 0.8f)
        )
        e.feed(both, 0L)
        val out = e.feed(both, 100L)
        assertEquals(1, out.tracks.size)
        assertEquals(DetectionKind.CLOSED, out.tracks[0].kind)
    }

    @Test
    fun confiance_est_la_moyenne_ferme_et_ouvert() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 100L, score = 0.9f)
        e.run(DetectionKind.OPEN, 200L, 500L, score = 0.8f)
        assertEquals(0.85f, e.lastConfidence, 0.001f)
    }

    @Test
    fun le_comptage_est_signale_une_seule_fois() {
        val e = ChestEngine()
        var signals = 0
        var t = 0L
        while (t <= 100L) {
            signals += e.feed(listOf(det(DetectionKind.CLOSED)), t).newlyCounted.size
            t += 100L
        }
        t = 200L
        while (t <= 2000L) {
            signals += e.feed(listOf(det(DetectionKind.OPEN)), t).newlyCounted.size
            t += 100L
        }
        assertEquals(1, signals)
    }

    @Test
    fun deux_coffres_sont_comptes_separement() {
        val e = ChestEngine()
        val closed = listOf(
            det(DetectionKind.CLOSED, 100f, 100f),
            det(DetectionKind.CLOSED, 500f, 300f)
        )
        val open = listOf(
            det(DetectionKind.OPEN, 100f, 100f),
            det(DetectionKind.OPEN, 500f, 300f)
        )
        e.feed(closed, 0L)
        e.feed(closed, 100L)
        var t = 200L
        while (t <= 500L) {
            e.feed(open, t)
            t += 100L
        }
        assertEquals(2, e.count)
    }

    @Test
    fun nouveau_coffre_apres_la_perte_du_precedent_est_compte() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 100L)
        e.run(DetectionKind.OPEN, 200L, 500L)
        assertEquals(1, e.count)

        e.feed(emptyList(), 2100L)

        e.run(DetectionKind.CLOSED, 2200L, 2300L)
        e.run(DetectionKind.OPEN, 2400L, 2700L)
        assertEquals(2, e.count)
    }

    @Test
    fun la_couleur_nest_pas_calculee_avant_louverture() {
        val e = ChestEngine()
        var calls = 0
        e.process(listOf(det(DetectionKind.CLOSED)), 0L) { calls++; 0f }
        e.process(listOf(det(DetectionKind.CLOSED)), 100L) { calls++; 0f }
        e.process(listOf(det(DetectionKind.OPEN)), 200L) { calls++; 0f }
        assertEquals(0, calls)
    }

    @Test
    fun or_stable_apres_ouverture_donne_treasure_found() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 100L)
        e.run(DetectionKind.OPEN, 200L, 500L)
        var found = false
        var t = 600L
        while (t <= 1000L) {
            val out = e.feed(listOf(det(DetectionKind.OPEN)), t, gold = 0.8f)
            if (out.events.any { it.to == ChestPhase.TREASURE_FOUND }) found = true
            t += 100L
        }
        assertTrue(found)
        assertEquals(1, e.count)
    }

    @Test
    fun reset_remet_tout_a_zero() {
        val e = ChestEngine()
        e.run(DetectionKind.CLOSED, 0L, 100L)
        e.run(DetectionKind.OPEN, 200L, 500L)
        e.reset()
        assertEquals(0, e.count)
        assertEquals(0f, e.lastConfidence, 0.0001f)
        val out = e.feed(emptyList(), 600L)
        assertTrue(out.tracks.isEmpty())
    }
}
