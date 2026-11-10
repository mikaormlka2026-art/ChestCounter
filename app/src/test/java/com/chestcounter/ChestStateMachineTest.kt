package com.chestcounter

import com.chestcounter.detect.ChestStateMachine
import com.chestcounter.detect.Observation
import org.junit.Assert.assertEquals
import org.junit.Test

class ChestStateMachineTest {

    // Horloge simulée : chaque image avance de 100 ms
    private var t = 0L

    private fun feed(sm: ChestStateMachine, obs: Observation, n: Int): Int {
        var counted = 0
        repeat(n) {
            if (sm.update(obs, t)) counted++
            t += 100
        }
        return counted
    }

    private fun openOneChest(sm: ChestStateMachine): Int {
        feed(sm, Observation.CLOSED, 3)
        return feed(sm, Observation.OPEN, 3)
    }

    @Test
    fun coffreNormal_compteUneFois() {
        val sm = ChestStateMachine()
        assertEquals(1, openOneChest(sm))
    }

    @Test
    fun ouvertSansEtreVuFerme_neCompteRien() {
        val sm = ChestStateMachine()
        assertEquals(0, feed(sm, Observation.OPEN, 10))
    }

    @Test
    fun coffreResteOuvert_pasDeDoublon() {
        val sm = ChestStateMachine()
        assertEquals(1, openOneChest(sm))
        assertEquals(0, feed(sm, Observation.OPEN, 20))
    }

    @Test
    fun deuxCoffresEspaces_comptePourDeux() {
        val sm = ChestStateMachine()
        var total = openOneChest(sm)
        feed(sm, Observation.NONE, 5)
        t += 5000
        total += openOneChest(sm)
        assertEquals(2, total)
    }

    @Test
    fun deuxCoffresTropRapproches_cooldownBloqueLeSecond() {
        val sm = ChestStateMachine()
        var total = openOneChest(sm)
        feed(sm, Observation.NONE, 5)
        total += openOneChest(sm)
        assertEquals(1, total)
    }

    @Test
    fun fermeInterrompu_neSArmePas() {
        val sm = ChestStateMachine()
        feed(sm, Observation.CLOSED, 2)
        feed(sm, Observation.NONE, 1)
        feed(sm, Observation.CLOSED, 1)
        assertEquals(0, feed(sm, Observation.OPEN, 3))
    }

    @Test
    fun ouvertClignotant_neCompteRien() {
        val sm = ChestStateMachine()
        feed(sm, Observation.CLOSED, 3)
        feed(sm, Observation.OPEN, 2)
        feed(sm, Observation.CLOSED, 1)
        assertEquals(0, feed(sm, Observation.OPEN, 2))
    }

    @Test
    fun timeoutArmement_desarmeApresTropDImagesVides() {
        val sm = ChestStateMachine()
        feed(sm, Observation.CLOSED, 3)
        feed(sm, Observation.NONE, 20)
        assertEquals(0, feed(sm, Observation.OPEN, 3))
    }
}
