package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import com.chestcounter.fusion.ChestPhase
import com.chestcounter.fusion.ChestPhaseTracker
import com.chestcounter.track.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ChestPhaseTrackerTest {

    private fun tr(id: Int, kind: DetectionKind, visible: Boolean = true) = Track(
        id, kind, BoundingBox(0f, 0f, 10f, 10f), 0.9f, 0L, 0L, 5, visible
    )

    private fun ChestPhaseTracker.step(
        tracks: List<Track>,
        t: Long,
        gold: Map<Int, Float> = emptyMap(),
        lost: List<Int> = emptyList()
    ) = update(tracks, gold, lost, t)

    private fun toOpened(p: ChestPhaseTracker) {
        p.step(listOf(tr(1, DetectionKind.CLOSED)), 0L)
        p.step(listOf(tr(1, DetectionKind.OPEN)), 100L)
        p.step(listOf(tr(1, DetectionKind.OPEN)), 400L)
    }

    @Test
    fun coffre_ferme_nouveau_est_detected() {
        val p = ChestPhaseTracker()
        val ev = p.step(listOf(tr(1, DetectionKind.CLOSED)), 0L)
        assertEquals(1, ev.size)
        assertNull(ev[0].from)
        assertEquals(ChestPhase.DETECTED, ev[0].to)
        assertTrue(ev[0].sawClosed)
        assertEquals(ChestPhase.DETECTED, p.phaseOf(1))
    }

    @Test
    fun coffre_vu_directement_ouvert_na_pas_vu_ferme() {
        val p = ChestPhaseTracker()
        val ev = p.step(listOf(tr(1, DetectionKind.OPEN)), 0L)
        assertEquals(ChestPhase.OPENED, ev[0].to)
        assertFalse(ev[0].sawClosed)
    }

    @Test
    fun ferme_puis_ouvert_passe_par_opening_puis_opened() {
        val p = ChestPhaseTracker()
        p.step(listOf(tr(1, DetectionKind.CLOSED)), 0L)
        p.step(listOf(tr(1, DetectionKind.OPEN)), 100L)
        assertEquals(ChestPhase.OPENING, p.phaseOf(1))
        p.step(listOf(tr(1, DetectionKind.OPEN)), 300L)
        assertEquals(ChestPhase.OPENING, p.phaseOf(1))
        p.step(listOf(tr(1, DetectionKind.OPEN)), 400L)
        assertEquals(ChestPhase.OPENED, p.phaseOf(1))
    }

    @Test
    fun ouverture_interrompue_revient_a_detected() {
        val p = ChestPhaseTracker()
        p.step(listOf(tr(1, DetectionKind.CLOSED)), 0L)
        p.step(listOf(tr(1, DetectionKind.OPEN)), 100L)
        p.step(listOf(tr(1, DetectionKind.CLOSED)), 200L)
        assertEquals(ChestPhase.DETECTED, p.phaseOf(1))
    }

    @Test
    fun piste_ouverte_invisible_ne_declenche_rien() {
        val p = ChestPhaseTracker()
        p.step(listOf(tr(1, DetectionKind.CLOSED)), 0L)
        p.step(listOf(tr(1, DetectionKind.OPEN, visible = false)), 100L)
        assertEquals(ChestPhase.DETECTED, p.phaseOf(1))
    }

    @Test
    fun or_stable_donne_treasure_found() {
        val p = ChestPhaseTracker()
        toOpened(p)
        p.step(listOf(tr(1, DetectionKind.OPEN)), 500L, mapOf(1 to 0.8f))
        assertEquals(ChestPhase.OPENED, p.phaseOf(1))
        p.step(listOf(tr(1, DetectionKind.OPEN)), 700L, mapOf(1 to 0.8f))
        assertEquals(ChestPhase.TREASURE_FOUND, p.phaseOf(1))
    }

    @Test
    fun or_bref_ne_suffit_pas() {
        val p = ChestPhaseTracker()
        toOpened(p)
        p.step(listOf(tr(1, DetectionKind.OPEN)), 500L, mapOf(1 to 0.8f))
        p.step(listOf(tr(1, DetectionKind.OPEN)), 600L, mapOf(1 to 0f))
        p.step(listOf(tr(1, DetectionKind.OPEN)), 700L, mapOf(1 to 0.8f))
        p.step(listOf(tr(1, DetectionKind.OPEN)), 800L, mapOf(1 to 0.8f))
        assertEquals(ChestPhase.OPENED, p.phaseOf(1))
    }

    @Test
    fun perdu_avant_ouverture_donne_lost_et_disparait() {
        val p = ChestPhaseTracker()
        p.step(listOf(tr(1, DetectionKind.CLOSED)), 0L)
        val ev = p.step(emptyList(), 2000L, lost = listOf(1))
        assertEquals(1, ev.size)
        assertEquals(ChestPhase.LOST, ev[0].to)
        assertNull(p.phaseOf(1))
    }

    @Test
    fun perdu_apres_ouverture_donne_cooldown() {
        val p = ChestPhaseTracker()
        toOpened(p)
        val ev = p.step(emptyList(), 1000L, lost = listOf(1))
        assertEquals(ChestPhase.OPENED, ev[0].from)
        assertEquals(ChestPhase.COOLDOWN, ev[0].to)
        assertTrue(ev[0].sawClosed)
        assertEquals(ChestPhase.COOLDOWN, p.phaseOf(1))
    }

    @Test
    fun perdu_apres_tresor_donne_cooldown() {
        val p = ChestPhaseTracker()
        toOpened(p)
        p.step(listOf(tr(1, DetectionKind.OPEN)), 500L, mapOf(1 to 0.8f))
        p.step(listOf(tr(1, DetectionKind.OPEN)), 700L, mapOf(1 to 0.8f))
        val ev = p.step(emptyList(), 1000L, lost = listOf(1))
        assertEquals(ChestPhase.TREASURE_FOUND, ev[0].from)
        assertEquals(ChestPhase.COOLDOWN, ev[0].to)
    }

    @Test
    fun cooldown_se_termine_apres_le_delai() {
        val p = ChestPhaseTracker()
        toOpened(p)
        p.step(emptyList(), 1000L, lost = listOf(1))
        p.step(emptyList(), 5999L)
        assertEquals(ChestPhase.COOLDOWN, p.phaseOf(1))
        p.step(emptyList(), 6000L)
        assertNull(p.phaseOf(1))
    }

    @Test
    fun coffre_deja_ouvert_perdu_garde_saw_closed_faux() {
        val p = ChestPhaseTracker()
        p.step(listOf(tr(1, DetectionKind.OPEN)), 0L)
        val ev = p.step(emptyList(), 1000L, lost = listOf(1))
        assertEquals(ChestPhase.COOLDOWN, ev[0].to)
        assertFalse(ev[0].sawClosed)
    }

    @Test
    fun deux_coffres_evoluent_independamment() {
        val p = ChestPhaseTracker()
        p.step(listOf(tr(1, DetectionKind.CLOSED), tr(2, DetectionKind.CLOSED)), 0L)
        p.step(listOf(tr(1, DetectionKind.OPEN), tr(2, DetectionKind.CLOSED)), 100L)
        assertEquals(ChestPhase.OPENING, p.phaseOf(1))
        assertEquals(ChestPhase.DETECTED, p.phaseOf(2))
    }

    @Test
    fun reset_efface_tout() {
        val p = ChestPhaseTracker()
        p.step(listOf(tr(1, DetectionKind.CLOSED)), 0L)
        p.reset()
        assertNull(p.phaseOf(1))
    }
}
