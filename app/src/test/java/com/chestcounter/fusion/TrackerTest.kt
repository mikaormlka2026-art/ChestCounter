package com.chestcounter.fusion

import org.junit.Assert.*
import org.junit.Test

class TrackerTest {
    private val a = Box(0.4f, 0.4f, 0.2f, 0.2f)

    @Test fun identifiantStable() {
        val t = Tracker(); val id = t.update(listOf(a), 0).ids[0]
        assertEquals(id, t.update(listOf(a), 100).ids[0])
    }
    @Test fun deuxCoffres_identifiantsDistincts() {
        val r = Tracker().update(listOf(a, Box(0.1f, 0.1f, 0.2f, 0.2f)), 0)
        assertNotEquals(r.ids[0], r.ids[1])
    }
    @Test fun mouvementCamera_gardeLIdentifiant() {
        val t = Tracker(); val id = t.update(listOf(a), 0).ids[0]
        var b = a
        for (i in 1..20) { b = b.copy(x = b.x + 0.04f); assertEquals(id, t.update(listOf(b), i * 100L).ids[0]) }
    }
    @Test fun reapparitionRapide_memeIdentifiant() {
        val t = Tracker(); val id = t.update(listOf(a), 0).ids[0]
        assertEquals(listOf(id), t.update(emptyList(), 1000).unseen)
        assertEquals(id, t.update(listOf(a), 2000).ids[0])
    }
    @Test fun absenceLongue_expireEtNouvelId() {
        val t = Tracker(); val id = t.update(listOf(a), 0).ids[0]
        val r = t.update(listOf(a), 6000)
        assertEquals(listOf(id), r.expired); assertNotEquals(id, r.ids[0])
    }
}
