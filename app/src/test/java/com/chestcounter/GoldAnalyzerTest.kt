package com.chestcounter

import com.chestcounter.detect.GoldAnalyzer
import com.chestcounter.detect.GoldParams
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GoldAnalyzerTest {

    private val gold = 0xFFE8B010.toInt()
    private val gray = 0xFF808080.toInt()
    private val blue = 0xFF2040E0.toInt()
    private val red = 0xFFFF0000.toInt()
    private val brownDark = 0xFF503C0A.toInt()
    private val paleYellow = 0xFFFFFAC8.toInt()

    private val p = GoldParams()

    @Test
    fun or_est_reconnu() {
        assertTrue(GoldAnalyzer.isGold(gold, p))
    }

    @Test
    fun gris_bleu_rouge_rejetes() {
        assertFalse(GoldAnalyzer.isGold(gray, p))
        assertFalse(GoldAnalyzer.isGold(blue, p))
        assertFalse(GoldAnalyzer.isGold(red, p))
    }

    @Test
    fun trop_sombre_ou_trop_pale_rejete() {
        assertFalse(GoldAnalyzer.isGold(brownDark, p))
        assertFalse(GoldAnalyzer.isGold(paleYellow, p))
    }

    @Test
    fun noir_et_blanc_rejetes() {
        assertFalse(GoldAnalyzer.isGold(0xFF000000.toInt(), p))
        assertFalse(GoldAnalyzer.isGold(0xFFFFFFFF.toInt(), p))
    }

    @Test
    fun zone_entierement_doree() {
        val px = IntArray(100) { gold }
        assertEquals(1f, GoldAnalyzer.ratio(px, 10, 10, 1), 0.0001f)
    }

    @Test
    fun zone_grise_donne_zero() {
        val px = IntArray(100) { gray }
        assertEquals(0f, GoldAnalyzer.ratio(px, 10, 10, 1), 0.0001f)
    }

    @Test
    fun moitie_doree() {
        val px = IntArray(100) { if (it < 50) gold else gray }
        assertEquals(0.5f, GoldAnalyzer.ratio(px, 10, 10, 1), 0.0001f)
    }

    @Test
    fun echantillonnage_uniforme_donne_meme_resultat() {
        val px = IntArray(400) { gold }
        assertEquals(1f, GoldAnalyzer.ratio(px, 20, 20, 3), 0.0001f)
    }

    @Test
    fun tailles_invalides_donnent_zero() {
        assertEquals(0f, GoldAnalyzer.ratio(IntArray(0), 0, 0, 1), 0.0001f)
        assertEquals(0f, GoldAnalyzer.ratio(IntArray(5), 10, 10, 1), 0.0001f)
    }

    @Test
    fun parametres_personnalises() {
        val strict = GoldParams(hueMin = 50f, hueMax = 60f)
        assertFalse(GoldAnalyzer.isGold(gold, strict))
    }
}
