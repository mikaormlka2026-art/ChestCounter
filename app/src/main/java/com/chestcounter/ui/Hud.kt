package com.chestcounter.ui

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import com.chestcounter.capture.CaptureService
import com.chestcounter.data.AppDb
import com.chestcounter.data.ChestEvent
import com.chestcounter.detect.TemplateKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Bulle flottante : violet sombre, déplaçable au doigt.
 * Un appui simple sur le fond de la bulle la réduit ou la déplie (mode compact).
 * Le bouton « Calibrer » ouvre le réglage de la zone, un cadre par-dessus le jeu,
 * et les boutons « Modèle fermé » / « Modèle ouvert » (enregistrement immédiat).
 * À appeler depuis le thread principal : show(), update(texte), hide().
 */
class Hud(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("hud_prefs", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var dot: View? = null
    private var pulse: ObjectAnimator? = null
    private var labelRow: LinearLayout? = null
    private var countView: TextView? = null
    private var targetView: TextView? = null
    private var detailView: TextView? = null
    private var infoView: TextView? = null
    private var feedbackView: TextView? = null
    private var actionRow: LinearLayout? = null
    private var panel: LinearLayout? = null
    private var calibToggle: TextView? = null
    private var bar: SoftProgress? = null
    private var barAnim: ValueAnimator? = null
    private var lastCount = -1
    private var compact = false
    private var calibOpen = false

    private var frameView: ZoneFrame? = null
    private var scope: CoroutineScope? = null
    private var statusJob: Job? = null
    private val clearFeedback = Runnable { feedbackView?.text = "" }

    // Zone du coffre, en pourcentage de l'écran (mémorisée entre les sessions).
    private var zl = prefs.getInt("zl", 40)
    private var zt = prefs.getInt("zt", 30)
    private var zr = prefs.getInt("zr", 60)
    private var zb = prefs.getInt("zb", 65)

    private val countRegex = Regex("""Coffres\s+(\d+)\s*/\s*(\d+)""")

    private fun dp(v: Int): Int = (v * density).toInt()

    fun show() {
        if (root != null) return

        val card = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            minimumWidth = dp(168)
            setPadding(dp(16), dp(12), dp(16), dp(14))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xE6141028.toInt(), 0xE62A1760.toInt())
            ).apply {
                cornerRadius = dp(22).toFloat()
                setStroke(dp(1), 0x66B388FF)
            }
            alpha = 0f
        }

        // Ligne du haut : point qui respire + petit titre
        val dotView = View(context).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFF9CFFD9.toInt())
            }
        }
        val label = TextView(context).apply {
            text = "CHEST COUNTER"
            textSize = 10f
            letterSpacing = 0.14f
            setTextColor(0xFFB8A8E8.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val top = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(dotView, LinearLayout.LayoutParams(dp(8), dp(8)).apply { marginEnd = dp(8) })
            addView(label)
        }
        card.addView(top)

        // Gros chiffre + objectif
        val count = TextView(context).apply {
            text = "0"
            textSize = 32f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        val target = TextView(context).apply {
            text = " / 1000"
            textSize = 13f
            setTextColor(0xFFA99AD6.toInt())
            setPadding(0, 0, 0, dp(5))
        }
        val countRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.BOTTOM
            addView(count)
            addView(target)
        }
        card.addView(countRow, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(4) })

        // Barre de progression douce
        val progress = SoftProgress(context)
        card.addView(progress, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(6)
        ).apply { topMargin = dp(8) })

        // Ligne de scores en direct
        val detail = TextView(context).apply {
            text = "En attente…"
            textSize = 10f
            setTextColor(0xFF8F82BF.toInt())
            typeface = Typeface.MONOSPACE
        }
        card.addView(detail, wrapParams(top = 8))

        // Ligne d'info : images reçues + modèles enregistrés
        val info = TextView(context).apply {
            text = "Images reçues 0"
            textSize = 10f
            setTextColor(0xFF8F82BF.toInt())
            typeface = Typeface.MONOSPACE
        }
        card.addView(info, wrapParams(top = 2))

        // Retour visible (ex. « Modèle fermé enregistré »)
        val feedback = TextView(context).apply {
            text = ""
            textSize = 11f
            setTextColor(0xFF9CFFD9.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        }
        card.addView(feedback, wrapParams(top = 4))

        // Boutons : -1 / +1 / Calibrer
        val minusOne = pill("-1", 12) { adjust(-1) }
        val plusOne = pill("+1", 12) { adjust(1) }
        val toggle = pill("Calibrer", 10) {
            calibOpen = !calibOpen
            calibToggle?.text = if (calibOpen) "Fermer" else "Calibrer"
            refreshLayout()
        }
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(minusOne, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(plusOne, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
            addView(toggle, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.6f).apply { marginStart = dp(6) })
        }
        card.addView(actions, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        // Panneau de calibration (caché au départ)
        val zoneTitle = TextView(context).apply {
            text = "ZONE DU COFFRE"
            textSize = 10f
            letterSpacing = 0.14f
            setTextColor(0xFFB8A8E8.toInt())
        }
        val closedBtn = pill("Modèle fermé", 8) { sendTemplate(TemplateKind.CLOSED) }
        val openBtn = pill("Modèle ouvert", 8) { sendTemplate(TemplateKind.OPEN) }
        val tplRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(closedBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(openBtn, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(6) })
        }
        val calib = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            addView(zoneTitle)
            addView(stepperRow("Gauche", { zl }) { changeZone(0, it) }, wrapMatch(top = 6))
            addView(stepperRow("Haut", { zt }) { changeZone(1, it) }, wrapMatch(top = 4))
            addView(stepperRow("Droite", { zr }) { changeZone(2, it) }, wrapMatch(top = 4))
            addView(stepperRow("Bas", { zb }) { changeZone(3, it) }, wrapMatch(top = 4))
            addView(tplRow, wrapMatch(top = 10))
        }
        card.addView(calib, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(10) })

        // Pas de focus : le jeu garde les touches, sauf sur les boutons de la bulle.
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = prefs.getInt("x", dp(24))
            y = prefs.getInt("y", dp(80))
        }

        installTouch(card)

        try {
            wm.addView(card, lp)
        } catch (t: Throwable) {
            return
        }

        root = card
        params = lp
        dot = dotView
        labelRow = top
        countView = count
        targetView = target
        detailView = detail
        infoView = info
        feedbackView = feedback
        actionRow = actions
        panel = calib
        calibToggle = toggle
        bar = progress
        compact = false
        calibOpen = false
        lastCount = -1

        scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        startStatusWatcher()

        // Entrée en douceur
        card.translationY = -dp(6).toFloat()
        card.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(350)
            .setInterpolator(DecelerateInterpolator())
            .start()

        // Le point respire
        pulse = ObjectAnimator.ofPropertyValuesHolder(
            dotView,
            PropertyValuesHolder.ofFloat(View.ALPHA, 1f, 0.35f),
            PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 0.7f),
            PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 0.7f)
        ).apply {
            duration = 1100
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            start()
        }
    }

    /** Attend un texte du type : "Coffres 12 / 1000\nF 62%  O 12%  31 ms". */
    fun update(text: String) {
        if (root == null) return
        val lines = text.split("\n")
        val m = countRegex.find(lines.firstOrNull() ?: "")
        if (m == null) {
            detailView?.text = text
            return
        }
        val count = m.groupValues[1].toIntOrNull() ?: 0
        val target = max(1, m.groupValues[2].toIntOrNull() ?: 1000)
        setCount(count, target)
        val detail = lines.drop(1).joinToString(" ").trim().replace("  ", " · ")
        if (detail.isNotEmpty()) detailView?.text = detail
    }

    fun hide() {
        statusJob?.cancel()
        statusJob = null
        scope?.cancel()
        scope = null
        handler.removeCallbacksAndMessages(null)
        hideFrame()
        pulse?.cancel()
        barAnim?.cancel()
        root?.animate()?.cancel()
        root?.let {
            try {
                wm.removeView(it)
            } catch (t: Throwable) {
            }
        }
        root = null
        params = null
        dot = null
        pulse = null
        labelRow = null
        countView = null
        targetView = null
        detailView = null
        infoView = null
        feedbackView = null
        actionRow = null
        panel = null
        calibToggle = null
        bar = null
        barAnim = null
        lastCount = -1
    }

    // ------------------------------------------------------------ calibration

    private fun templatesDir() = File(context.filesDir, "templates")

    private fun kindLabel(kind: TemplateKind) = if (kind == TemplateKind.CLOSED) "fermé" else "ouvert"

    private fun sendTemplate(kind: TemplateKind) {
        if (!CaptureService.status.value.running) {
            setFeedback("Capture non démarrée")
            return
        }
        try {
            context.startService(
                CaptureService.templateIntent(
                    context, kind,
                    zl / 100f, zt / 100f, zr / 100f, zb / 100f
                )
            )
            setFeedback("Modèle ${kindLabel(kind)} : envoi…")
        } catch (t: Throwable) {
            setFeedback("Envoi impossible : ${t.message}")
        }
    }

    private fun changeZone(which: Int, delta: Int) {
        when (which) {
            0 -> zl = (zl + delta).coerceIn(0, zr - MIN_GAP)
            1 -> zt = (zt + delta).coerceIn(0, zb - MIN_GAP)
            2 -> zr = (zr + delta).coerceIn(zl + MIN_GAP, 100)
            else -> zb = (zb + delta).coerceIn(zt + MIN_GAP, 100)
        }
        prefs.edit()
            .putInt("zl", zl).putInt("zt", zt)
            .putInt("zr", zr).putInt("zb", zb)
            .apply()
        frameView?.invalidate()
    }

    /** Correction manuelle : +1 ajoute un coffre, -1 retire le dernier. */
    private fun adjust(delta: Int) {
        scope?.launch(Dispatchers.IO) {
            try {
                val dao = AppDb.get(context.applicationContext).chestDao()
                if (delta > 0) {
                    dao.insert(
                        ChestEvent(timestampMs = System.currentTimeMillis(), confidence = 1f)
                    )
                } else {
                    dao.deleteLast()
                }
                handler.post { setFeedback(if (delta > 0) "+1 ajouté" else "Dernier coffre retiré") }
            } catch (t: Throwable) {
                handler.post { setFeedback("Erreur : ${t.message}") }
            }
        }
    }

    private fun startStatusWatcher() {
        statusJob?.cancel()
        statusJob = scope?.launch {
            CaptureService.status.collect { s ->
                val dir = templatesDir()
                val closedOk = File(dir, TemplateKind.CLOSED.fileName).exists()
                val openOk = File(dir, TemplateKind.OPEN.fileName).exists()
                handler.post { onStatus(s, closedOk, openOk) }
            }
        }
    }

    private fun onStatus(s: com.chestcounter.capture.CaptureStatus, closedOk: Boolean, openOk: Boolean) {
        if (root == null) return
        val black = if (s.framesReceived > 0 && s.lastFrameBlack) " · NOIRE" else ""
        infoView?.text = "Reçues ${s.framesReceived} · " +
            "Fermé ${if (closedOk) "✓" else "✗"} · Ouvert ${if (openOk) "✓" else "✗"}$black"

        val m = s.message
        if (m.startsWith("Modèle ") || m.startsWith("Échec") ||
            m.startsWith("Zone trop") || m.startsWith("Enregistrement")
        ) {
            setFeedback(m.replace("CLOSED", "fermé").replace("OPEN", "ouvert"))
        }
    }

    private fun setFeedback(text: String) {
        feedbackView?.text = text
        handler.removeCallbacks(clearFeedback)
        handler.postDelayed(clearFeedback, 5000)
    }

    // ------------------------------------------------------------ cadre visible

    private fun showFrame() {
        if (frameView != null) return
        val v = ZoneFrame(context)
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            if (Build.VERSION.SDK_INT >= 28) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        try {
            wm.addView(v, lp)
            frameView = v
        } catch (t: Throwable) {
            frameView = null
        }
    }

    private fun hideFrame() {
        frameView?.let {
            try {
                wm.removeView(it)
            } catch (t: Throwable) {
            }
        }
        frameView = null
    }

    // ------------------------------------------------------------------ interne

    private fun refreshLayout() {
        val v = if (compact) View.GONE else View.VISIBLE
        labelRow?.visibility = v
        detailView?.visibility = v
        infoView?.visibility = v
        feedbackView?.visibility = v
        actionRow?.visibility = v
        val showPanel = !compact && calibOpen
        panel?.visibility = if (showPanel) View.VISIBLE else View.GONE
        if (showPanel) showFrame() else hideFrame()
    }

    private fun setCount(count: Int, target: Int) {
        val cv = countView ?: return
        cv.text = count.toString()
        targetView?.text = " / $target"

        val b = bar ?: return
        val to = min(1f, count.toFloat() / target)
        barAnim?.cancel()
        barAnim = ValueAnimator.ofFloat(b.progress, to).apply {
            duration = 500
            interpolator = DecelerateInterpolator()
            addUpdateListener { b.progress = it.animatedValue as Float }
            start()
        }

        if (lastCount in 0 until count) pop(cv)
        lastCount = count
    }

    private fun pop(v: View) {
        v.animate().cancel()
        v.animate()
            .scaleX(1.18f).scaleY(1.18f)
            .setDuration(120)
            .withEndAction {
                v.animate()
                    .scaleX(1f).scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(OvershootInterpolator())
                    .start()
            }
            .start()
    }

    private fun toggleCompact() {
        compact = !compact
        refreshLayout()
    }

    private fun wrapParams(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun wrapMatch(top: Int) = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    ).apply { topMargin = dp(top) }

    private fun pill(text: String, hPad: Int, onClick: () -> Unit): TextView =
        TextView(context).apply {
            this.text = text
            textSize = 12f
            gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(hPad), dp(7), dp(hPad), dp(7))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor(0x663D2A7A)
                setStroke(dp(1), 0x66B388FF)
            }
            isClickable = true
            setOnClickListener { onClick() }
        }

    private fun stepperRow(label: String, get: () -> Int, change: (Int) -> Unit): LinearLayout {
        val name = TextView(context).apply {
            text = label
            textSize = 11f
            setTextColor(0xFFB8A8E8.toInt())
        }
        val value = TextView(context).apply {
            textSize = 11f
            setTextColor(0xFFFFFFFF.toInt())
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER
        }
        val refresh = { value.text = "${get()}%" }
        refresh()
        val minus = pill("−", 10) { change(-ZONE_STEP); refresh() }
        val plus = pill("+", 10) { change(ZONE_STEP); refresh() }
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(minus, LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(value, LinearLayout.LayoutParams(dp(46), LinearLayout.LayoutParams.WRAP_CONTENT))
            addView(plus, LinearLayout.LayoutParams(dp(36), LinearLayout.LayoutParams.WRAP_CONTENT))
        }
    }

    private fun installTouch(card: View) {
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var moved = false

        card.setOnTouchListener { _, e ->
            val p = params ?: return@setOnTouchListener false
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX
                    downY = e.rawY
                    startX = p.x
                    startY = p.y
                    moved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!moved && (abs(dx) > slop || abs(dy) > slop)) moved = true
                    if (moved) {
                        p.x = max(0, startX + dx.toInt())
                        p.y = max(0, startY + dy.toInt())
                        try {
                            wm.updateViewLayout(card, p)
                        } catch (t: Throwable) {
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (moved) {
                        prefs.edit().putInt("x", p.x).putInt("y", p.y).apply()
                    } else {
                        toggleCompact()
                    }
                    true
                }
                else -> false
            }
        }
    }

    /**
     * Cadre de la zone analysée, par-dessus le jeu. Ne reçoit aucune touche.
     * Le trait est dessiné À L'EXTÉRIEUR de la zone, pour ne pas finir dans le modèle enregistré.
     */
    private inner class ZoneFrame(context: Context) : View(context) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = dp(3).toFloat()
            color = 0xFFFFD54F.toInt()
        }

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val pad = line.strokeWidth / 2f
            canvas.drawRect(
                w * zl / 100f - pad,
                h * zt / 100f - pad,
                w * zr / 100f + pad,
                h * zb / 100f + pad,
                line
            )
        }
    }

    /** Barre arrondie avec un dégradé violet clair. */
    private class SoftProgress(context: Context) : View(context) {
        var progress: Float = 0f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x33FFFFFF }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            val w = width.toFloat()
            val h = height.toFloat()
            if (w <= 0f || h <= 0f) return
            val r = h / 2f
            canvas.drawRoundRect(0f, 0f, w, h, r, r, track)
            if (progress > 0f) {
                val fillW = max(h, w * progress)
                fill.shader = LinearGradient(
                    0f, 0f, fillW, 0f,
                    0xFF8E5BFF.toInt(), 0xFFE0B3FF.toInt(),
                    Shader.TileMode.CLAMP
                )
                canvas.drawRoundRect(0f, 0f, fillW, h, r, r, fill)
            }
        }
    }

    private companion object {
        const val ZONE_STEP = 2
        const val MIN_GAP = 6
    }
}
