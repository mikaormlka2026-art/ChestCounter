package com.chestcounter.fusion

import org.junit.Assert.*
import org.junit.Test

class ChestCoordinatorTest {
    private fun ev(s: Source, c: Claim, v: Double, t: Long) = Evidence(source = s, claim = c, value = v, tMs = t)
    private fun det(b: Box, c: Claim, t: Long) =
        Detection(b, listOf(ev(Source.VISION, c, 0.9, t), ev(Source.TEMPLATE, c, 0.8, t)))

    private val a = Box(0.1f, 0.1f, 0.2f, 0.2f)
    private val b = Box(0.6f, 0.6f, 0.2f, 0.2f)

    private fun scenario(co: ChestCoordinator, boxes: List<Box>, closed: Int = 4, opened: Int = 60) {
        var t = 0L
        repeat(closed) { t += 100; co.process(FrameInput(t, boxes.map { det(it, Claim.CLOSED, t) })) }
        repeat(opened) { t += 100; co.process(FrameInput(t, boxes.map { det(it, Claim.OPENED, t) })) }
    }

    @Test fun coffreSeulementVisible_nonCompte() {
        val co = ChestCoordinator(); var t = 0L
        repeat(100) { t += 100; co.process(FrameInput(t, listOf(det(a, Claim.CLOSED, t)))) }
        assertEquals(0, co.total)
    }
    @Test fun ouvertureComplete_compteUneFoisMemeSiLongue() {
        val co = ChestCoordinator(); scenario(co, listOf(a)); assertEquals(1, co.total)
    }
    @Test fun deuxCoffresSimultanes_deuxComptes() {
        val co = ChestCoordinator(); scenario(co, listOf(a, b)); assertEquals(2, co.total)
    }
    @Test fun coffreDejaOuvert_nonCompte() {
        val co = ChestCoordinator(); var t = 0L
        repeat(30) { t += 100; co.process(FrameInput(t, listOf(det(a, Claim.OPENED, t)))) }
        assertEquals(0, co.total)
    }
    @Test fun perteDeDetectionPuisReprise_pasDeDoubleComptage() {
        val co = ChestCoordinator(); var t = 0L
        repeat(4) { t += 100; co.process(FrameInput(t, listOf(det(a, Claim.CLOSED, t)))) }
        repeat(3) { t += 100; co.process(FrameInput(t, listOf(det(a, Claim.OPENED, t)))) }
        repeat(15) { t += 100; co.process(FrameInput(t, emptyList())) }
        repeat(5) { t += 100; co.process(FrameInput(t, listOf(det(a, Claim.OPENED, t)))) }
        assertEquals(1, co.total)
    }
    @Test fun audioGlobal_estFusionnePourUnCoffreSuivi() {
        val co = ChestCoordinator(); var t = 0L
        repeat(4) { t += 100; co.process(FrameInput(t, listOf(det(a, Claim.CLOSED, t)))) }
        repeat(3) {
            t += 100
            co.addGlobalEvidence(ev(Source.AUDIO, Claim.OPENED, 0.9, t))
            co.process(FrameInput(t, listOf(Detection(a, listOf(ev(Source.VISION, Claim.OPENED, 0.9, t))))))
        }
        assertEquals(1, co.total)
    }
}
