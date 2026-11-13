package com.chestcounter.capture

import android.util.Log
import com.chestcounter.config.PipelineConfig
import com.chestcounter.detect.BoundingBox
import com.chestcounter.detect.ChestDetector
import com.chestcounter.detect.Detection
import com.chestcounter.detect.DetectionResult
import com.chestcounter.detect.TemporalTransitions
import com.chestcounter.diagnostic.CandidateReport
import com.chestcounter.diagnostic.DiagnosticFormatter
import com.chestcounter.diagnostic.PipelineLog
import com.chestcounter.fusion.EngineOutput

/**
 * Étapes qui suivent le moteur de comptage : analyse temporelle des coffres suivis, recherche de
 * transitions fermé -> ouvert, journal et lignes de diagnostic. Tourne sur le fil de capture.
 */
class AdvancedAnalysis(private val pipeline: PipelineConfig = PipelineConfig()) {
    private val transitions = TemporalTransitions(pipeline.transitionWindowMs)
    private val log = PipelineLog(60) { line -> if (pipeline.logEnabled) Log.i(TAG, line) }
    private var lastVetoLogMs = -1L

    /**
     * Branché sur ChestEngine : autorise ou non un comptage selon la preuve temporelle.
     * Si la preuve est exigée, une preuve absente ou non mesurée REFUSE (jamais une autorisation).
     */
    fun countGate(trackId: Int, nowMs: Long): Boolean =
        if (pipeline.requireTemporalForCount) {
            transitions.gateStrict(trackId, nowMs, pipeline.minTransitionStrength)
        } else {
            true
        }

    fun reset() {
        transitions.clear()
        log.clear()
        lastVetoLogMs = -1L
    }

    fun recentLog(): List<String> = log.recent()

    fun afterEngine(
        detector: ChestDetector,
        r: DetectionResult,
        out: EngineOutput,
        nowMs: Long,
        frame: Int,
        phaseOf: (Int) -> String?
    ): List<String> {
        val reports = ArrayList<CandidateReport>()
        var temporalDone = 0
        for (t in out.tracks) {
            if (!t.visible) continue
            val temporal = if (temporalDone < MAX_TEMPORAL_TRACKS) {
                temporalDone++
                detector.analyzeTemporal(t.id, t.box, nowMs)
            } else null
            transitions.record(t.id, nowMs, t.kind, temporal)
            val det = bestDetection(t.box, r.detections)
            reports.add(
                CandidateReport(
                    trackId = t.id,
                    kind = t.kind,
                    box = t.box,
                    templateScore = t.score,
                    roi = det?.analysis?.roi,
                    temporal = temporal,
                    outcome = det?.analysis?.outcome,
                    vetoed = false,
                    state = phaseOf(t.id),
                    velocityX = t.velocityX,
                    velocityY = t.velocityY
                )
            )
        }
        for (v in r.vetoed) {
            reports.add(
                CandidateReport(
                    trackId = null,
                    kind = v.kind,
                    box = v.box,
                    templateScore = v.score,
                    roi = v.analysis?.roi,
                    temporal = null,
                    outcome = v.analysis?.outcome,
                    vetoed = true,
                    state = null,
                    velocityX = null,
                    velocityY = null
                )
            )
        }

        for (c in out.newlyCounted) {
            val rep = reports.firstOrNull { it.trackId == c.trackId }
            log.event(
                nowMs, frame, c.trackId, "COUNTED", c.confidence,
                if (rep != null) DiagnosticFormatter.scoreMap(rep) else emptyMap()
            )
        }
        for (e in out.events) {
            log.event(nowMs, frame, e.trackId, e.to.name, null)
        }
        if (r.vetoed.isNotEmpty() && (lastVetoLogMs < 0L || nowMs - lastVetoLogMs > 1000L)) {
            lastVetoLogMs = nowMs
            val rep = reports.firstOrNull { it.vetoed }
            log.event(
                nowMs, frame, null, "VETOED", rep?.outcome?.confidence,
                if (rep != null) DiagnosticFormatter.scoreMap(rep) else emptyMap()
            )
        }

        transitions.prune(nowMs)
        return DiagnosticFormatter.format(reports.take(MAX_REPORTS))
    }

    private fun bestDetection(box: BoundingBox, dets: List<Detection>): Detection? {
        var best: Detection? = null
        var bestIou = MIN_IOU
        for (d in dets) {
            val iou = box.iou(d.box)
            if (iou >= bestIou) {
                bestIou = iou
                best = d
            }
        }
        return best
    }

    private companion object {
        const val TAG = "ChestPipeline"
        const val MAX_REPORTS = 5
        const val MAX_TEMPORAL_TRACKS = 6
        const val MIN_IOU = 0.3f
    }
}
