package com.chestcounter.fusion

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

enum class Source { VISION, TEMPLATE, SILHOUETTE, COLOR, OCR, TEMPORAL, AUDIO }

/** Ce qu'un indice affirme à propos d'un coffre. */
enum class Claim { ABSENT, CLOSED, OPENING, OPENED, TREASURE }

/** Rectangle normalisé (0..1) : indépendant de la résolution réelle. */
data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    val cx get() = x + w / 2
    val cy get() = y + h / 2
    fun iou(o: Box): Float {
        val iw = min(x + w, o.x + o.w) - max(x, o.x)
        val ih = min(y + h, o.y + o.h) - max(y, o.y)
        if (iw <= 0f || ih <= 0f) return 0f
        val inter = iw * ih
        val union = w * h + o.w * o.h - inter
        return if (union <= 0f) 0f else inter / union
    }
    fun centerDist(o: Box): Float = hypot(cx - o.cx, cy - o.cy)
}

/**
 * Un indice produit par un module.
 * value = probabilité (0..1) que [claim] soit vrai ; confidence = fiabilité déclarée
 * par le module sur cette mesure ; quality = qualité de l'image/du signal (0..1).
 */
data class Evidence(
    val source: Source,
    val claim: Claim,
    val value: Double,
    val confidence: Double = 1.0,
    val quality: Double = 1.0,
    val tMs: Long,
    val chestId: Int? = null,
    val box: Box? = null,
)

/** Un coffre détecté dans une image, avec les indices de tous les modules sur sa région. */
data class Detection(val box: Box, val evidences: List<Evidence>)

data class FrameInput(
    val tMs: Long,
    val detections: List<Detection>,
    val unavailable: Set<Source> = emptySet(),
)
