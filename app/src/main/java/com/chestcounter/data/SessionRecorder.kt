package com.chestcounter.data

import android.content.Context
import com.chestcounter.track.Track
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

const val ENGINE_VERSION = "1.1.0-lot1-3"

/**
 * Enregistre une session sans jamais bloquer le fil de capture : file BORNÉE, un seul consommateur
 * (l'ordre est conservé). Observations perdues = comptées, la session est alors signalée incomplète.
 * Arrêt brutal : la session reste RUNNING puis devient INTERRUPTED au démarrage suivant.
 */
class SessionRecorder(
    context: Context,
    private val maxObservations: Int = 150_000
) {
    private sealed class Cmd {
        class Begin(val wallMs: Long, val monoMs: Long) : Cmd()
        class Frame(val rows: List<ObservationEntity>) : Cmd()
        class Opening(val epoch: Int, val trackId: Int, val tMonoMs: Long, val wallMs: Long, val confidence: Float) : Cmd()
        class Finish(
            val wallMs: Long,
            val received: Int,
            val processed: Int,
            val provisional: Int,
            val width: Int,
            val height: Int
        ) : Cmd()
    }

    private val dao = AppDb.get(context).sessionDao()
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Cmd>(capacity = 256)
    private val dropped = AtomicInteger(0)

    @Volatile private var epoch = 0
    @Volatile private var width = 0
    @Volatile private var height = 0
    @Volatile private var finished = false

    fun begin() {
        io.launch {
            var sessionId = 0L
            var stored = 0
            var limitHit = false
            var writeError: String? = null
            try {
                for (cmd in queue) {
                    when (cmd) {
                        is Cmd.Begin -> {
                            dao.closeStale(
                                SessionStatus.RUNNING, SessionStatus.INTERRUPTED,
                                cmd.wallMs, "Interrompue avant la fin (arrêt brutal ou service tué)"
                            )
                            sessionId = dao.insertSession(
                                SessionEntity(
                                    startedWallMs = cmd.wallMs, startedMonoMs = cmd.monoMs,
                                    endedWallMs = null, status = SessionStatus.RUNNING,
                                    engineVersion = ENGINE_VERSION, frameWidth = 0, frameHeight = 0,
                                    framesReceived = 0, framesProcessed = 0, observationCount = 0,
                                    droppedObservations = 0, provisionalCount = 0, incompleteReason = null
                                )
                            )
                        }
                        is Cmd.Frame -> if (sessionId != 0L) {
                            if (stored + cmd.rows.size > maxObservations) {
                                limitHit = true
                                dropped.addAndGet(cmd.rows.size)
                            } else {
                                try {
                                    dao.insertObservations(cmd.rows.map { it.copy(sessionId = sessionId) })
                                    stored += cmd.rows.size
                                } catch (t: Throwable) {
                                    writeError = t.message ?: "erreur d'écriture"
                                    dropped.addAndGet(cmd.rows.size)
                                }
                            }
                        }
                        is Cmd.Opening -> if (sessionId != 0L) {
                            try {
                                dao.insertOpening(
                                    OpeningRecord(
                                        sessionId = sessionId, epoch = cmd.epoch, trackId = cmd.trackId,
                                        tMonoMs = cmd.tMonoMs, wallMs = cmd.wallMs,
                                        confidence = cmd.confidence, source = OpeningSource.LIVE,
                                        attribution = "UNKNOWN_ATTRIBUTION",
                                        reason = "provisoire : en attente de l'analyse de fin de partie"
                                    )
                                )
                            } catch (t: Throwable) {
                                writeError = t.message ?: "erreur d'écriture"
                            }
                        }
                        is Cmd.Finish -> {
                            if (sessionId != 0L) {
                                val d = dropped.get()
                                val reason = when {
                                    limitHit -> "Limite de stockage atteinte ($maxObservations observations)"
                                    writeError != null -> "Erreur d'écriture : $writeError"
                                    d > 0 -> "$d observation(s) perdue(s) (base trop lente)"
                                    else -> null
                                }
                                dao.finishSession(
                                    sessionId, SessionStatus.COMPLETED, cmd.wallMs, cmd.received,
                                    cmd.processed, stored, d, cmd.provisional, cmd.width, cmd.height, reason
                                )
                            }
                            return@launch
                        }
                    }
                }
            } catch (t: Throwable) {
                // Base inaccessible : la session restera RUNNING puis INTERRUPTED, jamais présentée comme terminée.
            } finally {
                queue.close()
                io.cancel()
            }
        }
        queue.trySend(Cmd.Begin(System.currentTimeMillis(), android.os.SystemClock.elapsedRealtime()))
    }

    /** Le moteur a été remis à zéro : les identifiants de piste recommencent à 1. */
    fun onEngineReset() {
        epoch++
    }

    /** À appeler après chaque image traitée. N'enregistre que les coffres réellement VUS. */
    fun onFrame(nowMs: Long, tracks: List<Track>, frameW: Int, frameH: Int, phaseOf: (Int) -> String?) {
        if (finished) return
        width = frameW
        height = frameH
        val rows = ArrayList<ObservationEntity>(tracks.size)
        for (t in tracks) {
            if (!t.visible) continue
            rows.add(
                ObservationEntity(
                    sessionId = 0, epoch = epoch, tMonoMs = nowMs, trackId = t.id, kind = t.kind.name,
                    left = t.box.left, top = t.box.top, right = t.box.right, bottom = t.box.bottom,
                    score = t.score, visible = true, phase = phaseOf(t.id)
                )
            )
        }
        if (rows.isEmpty()) return
        if (queue.trySend(Cmd.Frame(rows)).isFailure) dropped.addAndGet(rows.size)
    }

    fun onOpening(trackId: Int, nowMs: Long, confidence: Float) {
        if (finished) return
        launchSend(Cmd.Opening(epoch, trackId, nowMs, System.currentTimeMillis(), confidence))
    }

    fun finish(framesReceived: Int, framesProcessed: Int, provisionalCount: Int) {
        if (finished) return
        finished = true
        launchSend(
            Cmd.Finish(System.currentTimeMillis(), framesReceived, framesProcessed, provisionalCount, width, height)
        )
    }

    /** Envoi non bloquant en conservant l'ordre ; si la file est pleine, envoi différé. */
    private fun launchSend(cmd: Cmd) {
        if (queue.trySend(cmd).isSuccess) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                queue.send(cmd)
            } catch (t: Throwable) {
                // file fermée : session déjà terminée ou base inaccessible
            }
        }
    }
}
