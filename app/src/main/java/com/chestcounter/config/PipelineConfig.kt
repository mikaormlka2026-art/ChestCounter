package com.chestcounter.config

/** OFF : rien n'est calculé ; OBSERVE : mesures et diagnostic sans effet ; VETO : écarte les candidats contredits. */
enum class FusionMode { OFF, OBSERVE, VETO }

/** Réglages du branchement des méthodes classiques dans l'appli. Valeurs de départ NON calibrées. */
data class PipelineConfig(
    val fusionMode: FusionMode = FusionMode.VETO,
    /** Seuls les candidats au-dessus de ce score de modèle sont analysés (économie de CPU). */
    val analyzeMinScore: Float = 0.60f,
    val maxAnalyzedPerFrame: Int = 4,
    /** Budget d'analyse par image : au-delà, les candidats restants ne sont pas analysés. */
    val analysisBudgetMs: Long = 80L,
    val temporalEnabled: Boolean = true,
    /** true : un coffre n'est compté que si un changement visible accompagne le passage fermé -> ouvert. */
    val requireTemporalForCount: Boolean = false,
    val minTransitionStrength: Float = 0.25f,
    val transitionWindowMs: Long = 3000L,
    /** Journal détaillé dans logcat (étiquette ChestPipeline). */
    val logEnabled: Boolean = false
)
