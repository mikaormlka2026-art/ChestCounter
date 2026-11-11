package com.chestcounter.fusion

import org.junit.Assert.*
import org.junit.Test

class FusionEngineTest {
    private fun ev(s: Source, c: Claim, v: Double, t: Long = 1000) =
        Evidence(source = s, claim = c, value = v, tMs = t)

    private val trois = listOf(
        ev(Source.VISION, Claim.OPENED, 0.9), ev(Source.TEMPLATE, Claim.OPENED, 0.8), ev(Source.TEMPORAL, Claim.OPENED, 0.8))

    @Test fun troisSourcesOuvert_confirme() {
        val r = FusionEngine().fuse(trois, 1000)
        assertEquals(Claim.OPENED, r.decision); assertTrue(r.confirmed); assertEquals(3, r.support.size)
    }
    @Test fun sourceUnique_nonConfirme() {
        val r = FusionEngine().fuse(listOf(ev(Source.VISION, Claim.OPENED, 0.95)), 1000)
        assertEquals(Claim.OPENED, r.decision); assertFalse(r.confirmed)
        assertTrue(r.explanation.contains("support insuffisant"))
    }
    @Test fun indicesContradictoires_nonConfirme() {
        val l = listOf(
            ev(Source.VISION, Claim.OPENED, 0.9), ev(Source.TEMPORAL, Claim.OPENED, 0.8),
            ev(Source.TEMPLATE, Claim.CLOSED, 0.9), ev(Source.COLOR, Claim.CLOSED, 0.9), ev(Source.OCR, Claim.CLOSED, 0.9))
        val r = FusionEngine().fuse(l, 1000)
        assertTrue(r.contradiction); assertFalse(r.confirmed)
    }
    @Test fun indiceTropAncien_ignore() {
        val r = FusionEngine().fuse(listOf(ev(Source.VISION, Claim.OPENED, 0.9, t = 0)), 5000)
        assertNull(r.decision); assertFalse(r.confirmed); assertTrue(r.ignored.isNotEmpty())
    }
    @Test fun moduleIndisponible_neCasseriePas() {
        val r = FusionEngine().fuse(trois, 1000, unavailable = setOf(Source.VISION))
        assertTrue(r.confirmed); assertFalse(Source.VISION in r.support)
        assertTrue(r.ignored.any { it.contains("VISION") })
    }
    @Test fun fiabiliteNulle_moduleIgnore() {
        val e = FusionEngine(); e.setReliability(Source.VISION, 0.0)
        val r = e.fuse(listOf(ev(Source.VISION, Claim.OPENED, 0.95)), 1000)
        assertNull(r.decision); assertFalse(r.confirmed)
    }
    @Test fun dernierInstantanParModule_remplaceLAncien() {
        val l = listOf(
            ev(Source.VISION, Claim.CLOSED, 0.9, t = 700), ev(Source.VISION, Claim.OPENED, 0.9, t = 1000),
            ev(Source.TEMPLATE, Claim.OPENED, 0.8, t = 1000))
        val r = FusionEngine().fuse(l, 1000)
        assertEquals(Claim.OPENED, r.decision); assertTrue(r.confirmed)
    }
}
