package com.chestcounter.detect

import kotlin.math.max
import kotlin.math.min

data class BoundingBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = if (isValid()) width * height else 0f

    fun isValid(): Boolean = right > left && bottom > top

    fun intersectionArea(other: BoundingBox): Float {
        val w = min(right, other.right) - max(left, other.left)
        val h = min(bottom, other.bottom) - max(top, other.top)
        return if (w <= 0f || h <= 0f) 0f else w * h
    }

    fun iou(other: BoundingBox): Float {
        val inter = intersectionArea(other)
        val union = area + other.area - inter
        return if (union <= 0f) 0f else inter / union
    }

    fun scaled(factor: Float): BoundingBox =
        BoundingBox(left * factor, top * factor, right * factor, bottom * factor)

    fun normalized(frameWidth: Float, frameHeight: Float): BoundingBox =
        BoundingBox(
            left / frameWidth,
            top / frameHeight,
            right / frameWidth,
            bottom / frameHeight
        )

    companion object {
        fun fromSize(x: Float, y: Float, width: Float, height: Float): BoundingBox =
            BoundingBox(x, y, x + width, y + height)
    }
}
