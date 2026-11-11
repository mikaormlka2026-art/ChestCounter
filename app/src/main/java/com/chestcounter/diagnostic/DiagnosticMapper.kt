package com.chestcounter.diagnostic

import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind

object DiagnosticMapper {

    fun mapBox(
        box: BoundingBox,
        frameW: Int,
        frameH: Int,
        viewW: Int,
        viewH: Int
    ): BoundingBox? {
        if (frameW <= 0 || frameH <= 0 || viewW <= 0 || viewH <= 0) return null
        if (!box.isValid()) return null
        val sx = viewW.toFloat() / frameW
        val sy = viewH.toFloat() / frameH
        val mapped = BoundingBox(
            box.left * sx,
            box.top * sy,
            box.right * sx,
            box.bottom * sy
        )
        return clamp(mapped, viewW, viewH)
    }

    fun clamp(box: BoundingBox, viewW: Int, viewH: Int): BoundingBox? {
        val l = box.left.coerceIn(0f, viewW.toFloat())
        val t = box.top.coerceIn(0f, viewH.toFloat())
        val r = box.right.coerceIn(0f, viewW.toFloat())
        val b = box.bottom.coerceIn(0f, viewH.toFloat())
        val out = BoundingBox(l, t, r, b)
        return if (out.isValid()) out else null
    }

    fun label(id: Int, kind: DetectionKind, score: Float): String {
        val k = if (kind == DetectionKind.CLOSED) "F" else "O"
        val pct = (score.coerceIn(0f, 1f) * 100).toInt()
        return "#$id $k $pct%"
    }
}
