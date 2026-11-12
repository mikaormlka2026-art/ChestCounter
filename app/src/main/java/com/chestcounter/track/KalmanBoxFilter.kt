package com.chestcounter.track

import com.chestcounter.detect.BoundingBox
import kotlin.math.max

/**
 * Réglages du filtre de Kalman d'une boîte (valeurs de départ NON calibrées).
 * Positions en pixels de la frame, temps en secondes.
 */
data class KalmanConfig(
    /** Variance d'accélération du centre (px/s²)² : plus elle est grande, plus le filtre suit les changements. */
    val accelerationVariance: Double = 40000.0,
    /** Bruit de marche aléatoire de la largeur et de la hauteur. */
    val sizeNoise: Double = 50.0,
    val positionMeasurementVariance: Double = 4.0,
    val sizeMeasurementVariance: Double = 9.0,
    val initialVelocityVariance: Double = 250000.0,
    /** Un écart de temps plus long est ramené à cette valeur avant la prédiction. */
    val maxDtSec: Double = 1.0,
    val minSizePx: Double = 2.0
)

/**
 * Filtre de Kalman d'une boîte : état (x, y, largeur, hauteur, vx, vy).
 * Le centre suit un modèle à vitesse constante ; la largeur et la hauteur suivent une marche aléatoire.
 * Ce n'est PAS un détecteur : il stabilise et prédit la position d'un coffre déjà détecté.
 * Kotlin pur, testable sur JVM.
 */
class KalmanBoxFilter(
    initial: BoundingBox,
    tMs: Long,
    private val cfg: KalmanConfig = KalmanConfig()
) {
    private class Axis(var p: Double, r: Double, velocityVariance: Double) {
        var v = 0.0
        var p00 = r
        var p01 = 0.0
        var p11 = velocityVariance

        fun predict(dt: Double, q: Double) {
            if (dt <= 0.0) return
            p += v * dt
            val dt2 = dt * dt
            val n00 = p00 + dt * (2.0 * p01 + dt * p11) + q * dt2 * dt2 / 4.0
            val n01 = p01 + dt * p11 + q * dt2 * dt / 2.0
            val n11 = p11 + q * dt2
            p00 = n00
            p01 = n01
            p11 = n11
        }

        fun update(z: Double, r: Double) {
            val s = p00 + r
            val k0 = p00 / s
            val k1 = p01 / s
            val y = z - p
            p += k0 * y
            v += k1 * y
            val n00 = (1.0 - k0) * p00
            val n01 = (1.0 - k0) * p01
            val n11 = p11 - k1 * p01
            p00 = n00
            p01 = n01
            p11 = n11
        }
    }

    private class Size1(var value: Double, r: Double) {
        var p = r

        fun predict(dt: Double, q: Double) {
            if (dt > 0.0) p += q * dt
        }

        fun update(z: Double, r: Double) {
            val k = p / (p + r)
            value += k * (z - value)
            p *= (1.0 - k)
        }
    }

    private val cx = Axis(initial.centerX.toDouble(), cfg.positionMeasurementVariance, cfg.initialVelocityVariance)
    private val cy = Axis(initial.centerY.toDouble(), cfg.positionMeasurementVariance, cfg.initialVelocityVariance)
    private val w = Size1(initial.width.toDouble(), cfg.sizeMeasurementVariance)
    private val h = Size1(initial.height.toDouble(), cfg.sizeMeasurementVariance)
    private var lastMs = tMs

    /** Nombre de mesures intégrées (la première compte). */
    var updates = 1
        private set

    /** Vitesse du centre en pixels par seconde. */
    val vx: Float get() = cx.v.toFloat()
    val vy: Float get() = cy.v.toFloat()

    /** Prédit jusqu'à tMs, puis corrige avec la mesure. Une boîte invalide est ignorée. */
    fun update(box: BoundingBox, tMs: Long) {
        if (!box.isValid()) return
        val dt = ((tMs - lastMs) / 1000.0).coerceIn(0.0, cfg.maxDtSec)
        cx.predict(dt, cfg.accelerationVariance)
        cy.predict(dt, cfg.accelerationVariance)
        w.predict(dt, cfg.sizeNoise)
        h.predict(dt, cfg.sizeNoise)
        cx.update(box.centerX.toDouble(), cfg.positionMeasurementVariance)
        cy.update(box.centerY.toDouble(), cfg.positionMeasurementVariance)
        w.update(box.width.toDouble(), cfg.sizeMeasurementVariance)
        h.update(box.height.toDouble(), cfg.sizeMeasurementVariance)
        lastMs = tMs
        updates++
    }

    /** Boîte filtrée à l'instant de la dernière mesure. */
    fun box(): BoundingBox = make(cx.p, cy.p, w.value, h.value)

    /**
     * Boîte prévue à l'instant tMs, sans modifier le filtre. L'extrapolation est plafonnée
     * à maxHorizonMs : un coffre caché longtemps n'est pas projeté indéfiniment.
     */
    fun predictedBox(tMs: Long, maxHorizonMs: Long): BoundingBox {
        val dt = ((tMs - lastMs) / 1000.0).coerceIn(0.0, max(0L, maxHorizonMs) / 1000.0)
        return make(cx.p + cx.v * dt, cy.p + cy.v * dt, w.value, h.value)
    }

    private fun make(x: Double, y: Double, width: Double, height: Double): BoundingBox {
        val ww = max(cfg.minSizePx, width)
        val hh = max(cfg.minSizePx, height)
        return BoundingBox(
            (x - ww / 2).toFloat(), (y - hh / 2).toFloat(),
            (x + ww / 2).toFloat(), (y + hh / 2).toFloat()
        )
    }
}
