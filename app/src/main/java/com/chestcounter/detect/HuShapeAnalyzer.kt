package com.chestcounter.detect

import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.imgproc.Imgproc

/** Calcule les 7 moments de Hu d'un contour. À créer après OpenCVLoader.initLocal(). */
class HuShapeAnalyzer {
    private val huMat = Mat()

    fun compute(contour: MatOfPoint): DoubleArray? {
        val m = Imgproc.moments(contour)
        if (m.m00 <= 0.0) return null
        Imgproc.HuMoments(m, huMat)
        val out = DoubleArray(7)
        huMat.get(0, 0, out)
        return out
    }

    fun release() {
        huMat.release()
    }
}
