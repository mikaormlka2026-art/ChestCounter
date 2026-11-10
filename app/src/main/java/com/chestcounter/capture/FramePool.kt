package com.chestcounter.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.media.Image

class FramePool {

    private var padded: Bitmap? = null
    private var cropped: Bitmap? = null
    private var canvas: Canvas? = null
    private val srcRect = Rect()
    private val dstRect = Rect()

    @Synchronized
    fun fill(image: Image): Bitmap {
        val plane = image.planes[0]
        val paddedWidth = plane.rowStride / plane.pixelStride
        val width = image.width
        val height = image.height

        var p = padded
        if (p == null || p.width != paddedWidth || p.height != height) {
            p?.recycle()
            p = Bitmap.createBitmap(paddedWidth, height, Bitmap.Config.ARGB_8888)
            padded = p
        }
        val buffer = plane.buffer
        buffer.rewind()
        p.copyPixelsFromBuffer(buffer)
        if (paddedWidth == width) return p

        var c = cropped
        if (c == null || c.width != width || c.height != height) {
            c?.recycle()
            c = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            cropped = c
            canvas = Canvas(c)
        }
        srcRect.set(0, 0, width, height)
        dstRect.set(0, 0, width, height)
        canvas?.drawBitmap(p, srcRect, dstRect, null)
        return c
    }

    @Synchronized
    fun release() {
        padded?.recycle()
        cropped?.recycle()
        padded = null
        cropped = null
        canvas = null
    }
}
