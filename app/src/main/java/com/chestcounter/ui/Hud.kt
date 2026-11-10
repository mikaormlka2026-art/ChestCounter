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
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Bulle flottante : violet sombre, douce, déplaçable au doigt.
 * Un appui simple la réduit ou la déplie.
 * À appeler depuis le thread principal : show(), update(texte), hide().
 */
class Hud(private val context: Context) {

    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val prefs = context.getSharedPreferences("hud_prefs", Context.MODE_PRIVATE)
    private val density = context.resources.displayMetrics.density
    private val slop = ViewConfiguration.get(context).scaledTouchSlop

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var dot: View? = null
    private var pulse: ObjectAnimator? = null
    private var labelRow: LinearLayout? = null
    private var countView: TextView? = null
    private var targetView: TextView? = null
    private var detailView: TextView? = null
    private var bar: SoftProgress? = null
    private var barAnim: ValueAnimator? = null
    private var lastCount = -1
    private var compact = false

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

        // Ligne de diagnostic
        val detail = TextView(context).apply {
            text = "En attente…"
            textSize = 10f
            setTextColor(0xFF8F82BF.toInt())
            typeface = Typeface.MONOSPACE
        }
        card.addView(detail, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) })

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
        bar = progress
        compact = false
        lastCount = -1

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
        bar = null
        barAnim = null
        lastCount = -1
    }

    // ------------------------------------------------------------------ interne

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
        val v = if (compact) View.GONE else View.VISIBLE
        labelRow?.visibility = v
        detailView?.visibility = v
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
}
