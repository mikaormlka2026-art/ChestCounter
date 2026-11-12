package com.chestcounter.diagnostic

import java.util.Locale

/**
 * Journal structuré : une ligne par événement important
 * (instant, image, piste, état, confiance, scores des méthodes).
 * Garde les dernières lignes en mémoire ; sink les envoie ailleurs (logcat). Kotlin pur.
 */
class PipelineLog(
    private val capacity: Int = 60,
    private val sink: ((String) -> Unit)? = null
) {
    private val lines = ArrayDeque<String>()

    @Synchronized
    fun event(
        tMs: Long,
        frame: Int,
        trackId: Int?,
        state: String,
        confidence: Float?,
        scores: Map<String, Float?> = emptyMap()
    ): String {
        val sb = StringBuilder()
        sb.append("t=").append(tMs).append(" f=").append(frame)
        sb.append(" track=").append(trackId?.toString() ?: "-")
        sb.append(" state=").append(state)
        if (confidence != null) sb.append(String.format(Locale.ROOT, " conf=%.2f", confidence))
        for ((k, v) in scores) {
            if (v != null) sb.append(String.format(Locale.ROOT, " %s=%.2f", k, v))
        }
        val line = sb.toString()
        lines.addLast(line)
        while (lines.size > capacity) lines.removeFirst()
        sink?.invoke(line)
        return line
    }

    @Synchronized
    fun recent(): List<String> = lines.toList()

    @Synchronized
    fun clear() {
        lines.clear()
    }
}
