package com.chestcounter.data

import android.content.Context
import com.chestcounter.analysis.ObsPoint
import com.chestcounter.analysis.PostGameAnalyzer
import com.chestcounter.analysis.SessionEvidence
import com.chestcounter.analysis.SessionReport
import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.DetectionKind
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

data class AnalysisOutcome(
    val sessionId: Long,
    val sessionStartMonoMs: Long,
    val report: SessionReport
)

sealed class AnalysisResult {
    object NoSession : AnalysisResult()
    class Done(val outcome: AnalysisOutcome) : AnalysisResult()
    class Failed(val message: String) : AnalysisResult()
}

/**
 * Analyse de fin de partie de la DERNIÈRE session. Idempotente : relancer remplace les
 * enregistrements REANALYSIS de la session, sans jamais toucher aux enregistrements LIVE
 * ni à l'ancienne table chest_events.
 */
object SessionAnalysisRunner {
    private const val WAIT_MS = 8000L
    private const val POLL_MS = 250L

    suspend fun analyzeLatest(context: Context, waitForEnd: Boolean): AnalysisResult =
        withContext(Dispatchers.IO) {
            try {
                val dao = AppDb.get(context).sessionDao()
                var session = dao.latestSession() ?: return@withContext AnalysisResult.NoSession

                // Le service écrit la fin de session après l'arrêt : on attend qu'elle soit enregistrée.
                if (waitForEnd) {
                    var waited = 0L
                    while (session.status == SessionStatus.RUNNING && waited < WAIT_MS) {
                        delay(POLL_MS)
                        waited += POLL_MS
                        session = dao.latestSession() ?: break
                    }
                }

                val obs = dao.observations(session.id).mapNotNull { o ->
                    val kind = kindOf(o.kind) ?: return@mapNotNull null
                    ObsPoint(o.epoch, o.trackId, o.tMonoMs, kind, BoundingBox(o.left, o.top, o.right, o.bottom), o.score)
                }

                val reasons = ArrayList<String>()
                session.incompleteReason?.let { reasons.add(it) }
                if (session.status == SessionStatus.RUNNING) reasons.add("fin de session non confirmée")

                // Aucun module joueur / indice d'interaction / autres joueurs pour l'instant :
                // les preuves sont donc vides (inconnues), jamais positives.
                val report = PostGameAnalyzer().analyze(obs, SessionEvidence(), reasons.isEmpty(), reasons)

                dao.deleteOpenings(session.id, OpeningSource.REANALYSIS)
                val records = report.events.map { e ->
                    val t = e.recon.firstOpenMs ?: session.startedMonoMs
                    OpeningRecord(
                        sessionId = session.id,
                        epoch = e.recon.epoch,
                        trackId = e.recon.trackId,
                        tMonoMs = t,
                        wallMs = session.startedWallMs + (t - session.startedMonoMs),
                        confidence = 0f, // non calibrée : ne pas lire comme une probabilité
                        source = OpeningSource.REANALYSIS,
                        attribution = e.verdict.name,
                        reason = e.reason
                    )
                }
                if (records.isNotEmpty()) dao.insertOpenings(records)
                if (session.status != SessionStatus.RUNNING) dao.setStatus(session.id, SessionStatus.ANALYZED)

                AnalysisResult.Done(AnalysisOutcome(session.id, session.startedMonoMs, report))
            } catch (c: CancellationException) {
                throw c
            } catch (t: Throwable) {
                AnalysisResult.Failed(t.message ?: "erreur inconnue")
            }
        }

    private fun kindOf(name: String): DetectionKind? =
        try {
            DetectionKind.valueOf(name)
        } catch (t: IllegalArgumentException) {
            null
        }
}
