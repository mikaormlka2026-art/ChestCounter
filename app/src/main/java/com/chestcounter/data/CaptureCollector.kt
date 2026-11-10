package com.chestcounter.data

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CollectReason { COUNTED, AMBIGUOUS, PERIODIC }

/**
 * Enregistre des captures d'écran pour constituer plus tard un jeu de données.
 * Désactivé par défaut. Le classement est provisoire : le nom du fichier indique
 * la raison et les scores, mais ce n'est jamais une annotation certaine.
 * À appeler sur le thread de capture, tant que l'image n'est pas libérée.
 */
class CaptureCollector(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var savedCount: Int = 0
        private set
    var lastError: String? = null
        private set

    private var lastSaveAt = 0L
    private var lastPeriodicAt = 0L

    fun isEnabled(): Boolean = prefs.getBoolean(KEY_ENABLED, false)

    /** Retourne la raison de la sauvegarde, ou null si rien n'a été enregistré. */
    fun maybeSave(
        bmp: Bitmap,
        nowMs: Long,
        closedScore: Float,
        openScore: Float,
        counted: Boolean
    ): CollectReason? {
        if (!isEnabled()) return null
        if (Build.VERSION.SDK_INT < 29) return null
        if (savedCount >= MAX_PER_SESSION) return null

        val best = maxOf(closedScore, openScore)
        val ambiguous = best >= AMBIGUOUS_MIN && best < AMBIGUOUS_MAX

        val reason: CollectReason = when {
            counted -> CollectReason.COUNTED
            ambiguous && nowMs - lastSaveAt >= MIN_GAP_MS -> CollectReason.AMBIGUOUS
            nowMs - lastPeriodicAt >= PERIODIC_MS -> CollectReason.PERIODIC
            else -> return null
        }

        val ok = save(bmp, reason, closedScore, openScore)
        lastSaveAt = nowMs
        if (reason == CollectReason.PERIODIC || !ok) lastPeriodicAt = nowMs
        if (!ok) return null
        savedCount++
        return reason
    }

    private fun save(
        bmp: Bitmap,
        reason: CollectReason,
        closedScore: Float,
        openScore: Float
    ): Boolean {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss_SSS", Locale.US).format(Date())
        val name = "cc_${stamp}_${reason.name.lowercase(Locale.US)}" +
            "_c${(closedScore * 100).toInt()}_o${(openScore * 100).toInt()}.jpg"

        val resolver = appContext.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, name)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/ChestCounter"
            )
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }

        val uri = try {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        } catch (e: Throwable) {
            null
        }
        if (uri == null) {
            lastError = "Création du fichier refusée"
            return false
        }

        return try {
            val out = resolver.openOutputStream(uri)
                ?: throw IllegalStateException("Flux d'écriture indisponible")
            out.use {
                check(bmp.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, it)) {
                    "Compression refusée"
                }
            }
            val done = ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }
            resolver.update(uri, done, null, null)
            lastError = null
            true
        } catch (e: Throwable) {
            try {
                resolver.delete(uri, null, null)
            } catch (e2: Throwable) {
            }
            lastError = e.message ?: "Erreur inconnue"
            false
        }
    }

    companion object {
        private const val PREFS = "collector_prefs"
        private const val KEY_ENABLED = "enabled"

        private const val MAX_PER_SESSION = 300
        private const val MIN_GAP_MS = 2000L
        private const val PERIODIC_MS = 15000L
        private const val AMBIGUOUS_MIN = 0.40f
        private const val AMBIGUOUS_MAX = 0.70f
        private const val JPEG_QUALITY = 90

        fun setEnabled(context: Context, enabled: Boolean) {
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_ENABLED, enabled)
                .apply()
        }

        fun isEnabled(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_ENABLED, false)
    }
}
