package com.chestcounter

import com.chestcounter.detect.ChestState
import com.chestcounter.detect.ChestStateMachine
import com.chestcounter.detect.Observation
import org.junit.Assert.assertEquals
import org.junit.Test

class ChestStateMachineTest {

    /** Envoie une image toutes les `step` ms, de `from` à `to` inclus. Retourne le nombre de coffres comptés. */
    private fun feed(
        m: ChestStateMachine,
        from: Long,
        to: Long,
        closed: Float,
        open: Float,
        step: Long = 100L
    ): Int {
        var counted = 0
        var t = from
        while (t <= to) {
            if (m.process(Observation(t, closed, open))) counted++
            t += step
        }
        return counted
    }

    @Test
    fun ferme_puis_ouvert_compte_un_coffre() {
        val m = ChestStateMachine()
        val c = feed(m, 0, 300, 0.9f, 0f) + feed(m, 400, 700, 0f, 0.9f)
        assertEquals(1, c)
        assertEquals(1, m.count)
        assertEquals(ChestState.COOLDOWN, m.state)
    }

    @Test
    fun ferme_seul_ne_compte_pas() {
        val m = ChestStateMachine()
        val c = feed(m, 0, 3000, 0.9f, 0f)
        assertEquals(0, c)
        assertEquals(0, m.count)
        assertEquals(ChestState.CLOSED_SEEN, m.state)
    }

    @Test
    fun ouvert_seul_sans_ferme_ne_compte_pas() {
        val m = ChestStateMachine()
        val c = feed(m, 0, 3000, 0f, 0.9f)
        assertEquals(0, c)
        assertEquals(ChestState.IDLE, m.state)
    }

    @Test
    fun apparition_trop_breve_du_ferme_est_ignoree() {
        val m = ChestStateMachine()
        val c = feed(m, 0, 200, 0.9f, 0f) + feed(m, 300, 1500, 0f, 0.9f)
        assertEquals(0, c)
        assertEquals(ChestState.IDLE, m.state)
    }

    @Test
    fun delai_trop_long_entre_ferme_et_ouvert_ne_compte_pas() {
        val m = ChestStateMachine()
        feed(m, 0, 300, 0.9f, 0f)
        feed(m, 400, 5000, 0f, 0f)
        assertEquals(ChestState.IDLE, m.state)
        val c = feed(m, 5100, 5500, 0f, 0.9f)
        assertEquals(0, c)
        assertEquals(0, m.count)
    }

    @Test
    fun ouvert_qui_reste_affiche_ne_recompte_pas() {
        val m = ChestStateMachine()
        feed(m, 0, 300, 0.9f, 0f)
        feed(m, 400, 700, 0f, 0.9f)
        val more = feed(m, 800, 20000, 0f, 0.9f)
        assertEquals(0, more)
        assertEquals(1, m.count)
        assertEquals(ChestState.COOLDOWN, m.state)
    }

    @Test
    fun pas_de_recomptage_tant_que_ouvert_reste_visible() {
        val m = ChestStateMachine()
        feed(m, 0, 300, 0.9f, 0f)
        feed(m, 400, 700, 0f, 0.9f)
        feed(m, 800, 20000, 0f, 0.9f)
        val c = feed(m, 20100, 20400, 0.9f, 0f) + feed(m, 20500, 20800, 0f, 0.9f)
        assertEquals(0, c)
        assertEquals(1, m.count)
    }

    @Test
    fun deuxieme_coffre_compte_apres_rearmement() {
        val m = ChestStateMachine()
        feed(m, 0, 300, 0.9f, 0f)
        feed(m, 400, 700, 0f, 0.9f)
        assertEquals(1, m.count)
        feed(m, 800, 7400, 0f, 0f)
        assertEquals(ChestState.IDLE, m.state)
        val c = feed(m, 7500, 7800, 0.9f, 0f) + feed(m, 7900, 8200, 0f, 0.9f)
        assertEquals(1, c)
        assertEquals(2, m.count)
    }

    @Test
    fun une_image_manquante_est_tolere() {
        val m = ChestStateMachine()
        feed(m, 0, 100, 0.9f, 0f)
        m.process(Observation(200, 0f, 0f))
        m.process(Observation(300, 0.9f, 0f))
        assertEquals(ChestState.CLOSED_SEEN, m.state)
    }

    @Test
    fun scores_ambigus_ne_declenchent_rien() {
        val m = ChestStateMachine()
        val c = feed(m, 0, 3000, 0.9f, 0.9f)
        assertEquals(0, c)
        assertEquals(ChestState.IDLE, m.state)
    }

    @Test
    fun scores_sous_le_seuil_ne_declenchent_rien() {
        val m = ChestStateMachine()
        val c = feed(m, 0, 1000, 0.5f, 0f) + feed(m, 1100, 2000, 0f, 0.5f)
        assertEquals(0, c)
        assertEquals(ChestState.IDLE, m.state)
    }

    @Test
    fun confiance_est_la_moyenne_des_deux_scores() {
        val m = ChestStateMachine()
        feed(m, 0, 300, 0.9f, 0f)
        feed(m, 400, 700, 0f, 0.8f)
        assertEquals(0.85f, m.lastConfidence, 0.001f)
    }

    @Test
    fun reset_remet_tout_a_zero() {
        val m = ChestStateMachine()
        feed(m, 0, 300, 0.9f, 0f)
        feed(m, 400, 700, 0f, 0.9f)
        m.reset()
        assertEquals(0, m.count)
        assertEquals(0f, m.lastConfidence, 0.0001f)
        assertEquals(ChestState.IDLE, m.state)
    }
}
