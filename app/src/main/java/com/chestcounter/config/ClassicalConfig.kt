package com.chestcounter.config

/**
 * Paramètres des méthodes de vision classique (lot 1 : HSV, Canny, contours, géométrie, Hu).
 * Toutes les valeurs sont des points de départ NON calibrés : à régler avec de vraies
 * captures GeForce NOW. HSV à l'échelle OpenCV (H 0..179, S et V 0..255).
 */
data class ClassicalConfig(
    // ROI normalisée (plus grand côté, en pixels)
    val roiSidePx: Int = 64,
    val minRoiSidePx: Int = 8,

    // HSV
    val hsvHueMin: Int = 17,
    val hsvHueMax: Int = 32,
    val hsvSatMin: Int = 100,
    val hsvValMin: Int = 100,
    val hsvDarkValMax: Int = 70,
    val hsvCellRatioMin: Float = 0.10f,
    val hsvFlatRatio: Float = 0.85f,
    val hsvFlatPenalty: Float = 0.25f,

    // Canny
    val blurKernel: Int = 3,
    val cannyLow: Double = 50.0,
    val cannyHigh: Double = 150.0,
    val closeKernel: Int = 3,
    val edgeDensityMin: Float = 0.02f,
    val edgeDensityIdealMin: Float = 0.08f,
    val edgeDensityIdealMax: Float = 0.30f,
    val edgeDensityMax: Float = 0.55f,

    // Contours
    val minContourAreaFrac: Float = 0.02f,
    val minFragmentArea: Double = 4.0,
    val contourCoverageMin: Float = 0.10f,
    val contourCoverageFull: Float = 0.45f,

    // Géométrie (largeur / hauteur)
    val aspectMin: Float = 0.5f,
    val aspectIdealMin: Float = 0.8f,
    val aspectIdealMax: Float = 1.8f,
    val aspectMax: Float = 2.5f,

    // Hu : plus la valeur est grande, plus la similarité est tolérante
    val huScale: Double = 1.0
)
