package com.chestcounter.fusion

import org.junit.Assert.*
import org.junit.Test

class ChestLifecycleTest {
    private fun res(c: Claim?) = FusionResult(
        scores = if (c == null) emptyMap() else mapOf(c to 0.9), decision = c, confirmed = c != null,
        confidence = 0.9, support = setOf(Source.VISION, Source.TEMPLATE), contradiction = false,
        ignored = emptyList(), explanation = "test")
    private fun ChestLifecycle.feed(c: Claim?, vararg ts: Long) = ts.map { update(res(c), it) }

    @Test fun ouvertureConfirmee_compteeUneSeuleFois() {
        val l = ChestLifecycle(1)
        val ev = l.feed(Claim.CLOSED, 0, 100) + l.feed(Claim.OPENED, 200, 300, 400, 500, 600)
        assertEquals(1, ev.count { it is ChestEvent.Counted })
        assertEquals(ChestState.OPENED, l.state)
    }
    @Test fun ouvertDesLaPremiereApparition_nonCompte() {
        val l = ChestLifecycle(1)
        val ev = l.feed(Claim.OPENED, 0, 100, 200, 300)
        assertEquals(0, ev.count { it is ChestEvent.Counted })
        assertEquals(1, ev.count { it is ChestEvent.IgnoredAlreadyOpen })
    }
    @Test fun ouvertureNonConfirmee_incertainSansComptage() {
        val l = ChestLifecycle(1)
        val ev = l.feed(Claim.CLOSED, 0, 100) + l.feed(Claim.OPENING, 200) + l.feed(null, 500, 1000, 2000, 3000, 4000, 5000)
        assertEquals(0, ev.count { it is ChestEvent.Counted })
        assertTrue(ev.any { it is ChestEvent.Uncertain })
    }
    @Test fun perteEtReprise_pendantOuverture_compteUneFois() {
        val l = ChestLifecycle(1)
        l.feed(Claim.CLOSED, 0, 100); l.feed(Claim.OPENING, 200)
        l.update(null, 1200); assertEquals(ChestState.LOST, l.state)
        val ev = l.feed(Claim.OPENED, 1300, 1400)
        assertEquals(1, ev.count { it is ChestEvent.Counted })
    }
    @Test fun disparitionPendantOuverture_incertain() {
        val l = ChestLifecycle(1)
        l.feed(Claim.CLOSED, 0, 100); l.feed(Claim.OPENING, 200); l.update(null, 1200)
        assertTrue(l.expire() is ChestEvent.Uncertain)
    }
    @Test fun apresCooldown_pasDeRecomptage() {
        val l = ChestLifecycle(1)
        l.feed(Claim.CLOSED, 0, 100)
        val ev = l.feed(Claim.OPENED, 200, 300) + (1..80).map { l.update(res(Claim.OPENED), 300L + it * 100) }
        assertEquals(1, ev.count { it is ChestEvent.Counted }); assertEquals(ChestState.COOLDOWN, l.state)
    }
}
