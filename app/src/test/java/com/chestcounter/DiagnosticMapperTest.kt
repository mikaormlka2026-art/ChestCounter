package com.chestcounter

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import com.chestcounter.diagnostic.DiagnosticMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DiagnosticMapperTest {

    @Test
    fun agrandissement_uniforme() {
        val m = DiagnosticMapper.mapBox(
            BoundingBox(100f, 50f, 200f, 150f), 1000, 500, 2000, 1000
        )
        assertNotNull(m)
        assertEquals(200f, m!!.left, 0.001f)
        assertEquals(100f, m.top, 0.001f)
        assertEquals(400f, m.right, 0.001f)
        assertEquals(300f, m.bottom, 0.001f)
    }

    @Test
    fun echelles_differentes_sur_x_et_y() {
        val m = DiagnosticMapper.mapBox(
            BoundingBox(10f, 20f, 30f, 40f), 100, 200, 200, 200
        )
        assertNotNull(m)
        assertEquals(20f, m!!.left, 0.001f)
        assertEquals(20f, m.top, 0.001f)
        assertEquals(60f, m.right, 0.001f)
        assertEquals(40f, m.bottom, 0.001f)
    }

    @Test
    fun tailles_invalides_donnent_null() {
        val b = BoundingBox(0f, 0f, 10f, 10f)
        assertNull(DiagnosticMapper.mapBox(b, 0, 100, 100, 100))
        assertNull(DiagnosticMapper.mapBox(b, 100, 0, 100, 100))
        assertNull(DiagnosticMapper.mapBox(b, 100, 100, 0, 100))
        assertNull(DiagnosticMapper.mapBox(b, 100, 100, 100, 0))
    }

    @Test
    fun boite_invalide_donne_null() {
        val bad = BoundingBox(50f, 50f, 10f, 10f)
        assertNull(DiagnosticMapper.mapBox(bad, 100, 100, 100, 100))
    }

    @Test
    fun boite_depassant_ecran_est_rognee() {
        val m = DiagnosticMapper.mapBox(
            BoundingBox(-10f, -10f, 50f, 50f), 100, 100, 100, 100
        )
        assertNotNull(m)
        assertEquals(0f, m!!.left, 0.001f)
        assertEquals(0f, m.top, 0.001f)
        assertEquals(50f, m.right, 0.001f)
        assertEquals(50f, m.bottom, 0.001f)
    }

    @Test
    fun boite_hors_ecran_donne_null() {
        val m = DiagnosticMapper.mapBox(
            BoundingBox(150f, 150f, 200f, 200f), 100, 100, 100, 100
        )
        assertNull(m)
    }

    @Test
    fun libelle_ferme() {
        assertEquals("#3 F 87%", DiagnosticMapper.label(3, DetectionKind.CLOSED, 0.874f))
    }

    @Test
    fun libelle_ouvert_plafonne_a_cent() {
        assertEquals("#12 O 100%", DiagnosticMapper.label(12, DetectionKind.OPEN, 1.5f))
    }

    @Test
    fun libelle_score_negatif_vaut_zero() {
        assertEquals("#1 F 0%", DiagnosticMapper.label(1, DetectionKind.CLOSED, -0.3f))
    }
}
