package com.chestcounter.config

enum class ProfileName { STRICT, BALANCED, PERMISSIVE }

/**
 * Configuration centralisée du détecteur.
 * Les valeurs des profils sont des points de départ NON calibrés :
 * elles doivent être réglées avec de vraies captures GeForce NOW.
 */
data class DetectionConfig(
    val profile: ProfileName = ProfileName.BALANCED,

    val templateThreshold: Float = 0.60f,
    val orbThreshold: Float = 0.30f,
    val hsvThreshold: Float = 0.30f,
    val edgeThreshold: Float = 0.30f,
    val huThreshold: Float = 0.40f,
    val hogThreshold: Float = 0.40f,
    val ssimThreshold: Float = 0.85f,
    val motionThreshold: Float = 0.30f,
    val trackingThreshold: Float = 0.30f,

    val possibleThreshold: Float = 0.35f,
    val probableThreshold: Float = 0.55f,
    val confirmedThreshold: Float = 0.75f,

    val confirmationFrames: Int = 3,
    val lostFrames: Int = 8,
    val cooldownDurationMs: Long = 5000L,

    val hogEnabled: Boolean = true,
    val orbEnabled: Boolean = true,
    val maxCandidatesPerFrame: Int = 8
) {
    companion object {
        fun forProfile(p: ProfileName): DetectionConfig = when (p) {
            ProfileName.STRICT -> DetectionConfig(
                profile = p,
                templateThreshold = 0.70f,
                possibleThreshold = 0.45f,
                probableThreshold = 0.65f,
                confirmedThreshold = 0.85f,
                confirmationFrames = 4,
                lostFrames = 6
            )
            ProfileName.BALANCED -> DetectionConfig(profile = p)
            ProfileName.PERMISSIVE -> DetectionConfig(
                profile = p,
                templateThreshold = 0.50f,
                possibleThreshold = 0.25f,
                probableThreshold = 0.45f,
                confirmedThreshold = 0.65f,
                confirmationFrames = 2,
                lostFrames = 12
            )
        }
    }
}
