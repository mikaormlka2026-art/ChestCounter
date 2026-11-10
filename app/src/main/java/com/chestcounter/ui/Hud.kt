package com.chestcounter.ui

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.chestcounter.capture.CaptureService
import com.chestcounter.data.GOAL
import com.chestcounter.detect.Observation

class Hud(
    private val context: Context,
    private val onAction: (String) -> Unit
) {
    private val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private val density = context.resources.displayMetrics.density

    private var root: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var countView: TextView? = null
    private var stateView: TextView? = null
    private var msgView: TextView? = null

    private fun dp(v: Int) = (v * density).toInt()

    private fun bg(color: Int, radius: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radius * density
        }

    private fun button(label: String, color: Int, action: String): TextView =
        TextView(context).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = bg(color, 8)
            setOnClickListener { onAction(action) }
        }

    private fun row(vararg views: View): LinearLayout =
        LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            views.forEachIndexed { i, v ->
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (i > 0) lp.leftMargin = dp(6)
                addView(v, lp)
            }
        }

    fun show() {
        if (root != null) return

        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(12))
            background = bg(Color.parseColor("#E61A1426"), 14)
        }

        val count = TextView(context).apply {
            setTextColor(Color.parseColor("#C4A7FF"))
            textSize = 20f
            text = "0 / $GOAL"
            gravity = Gravity.CENTER
        }
        val state = TextView(context).apply {
            setTextColor(Color.parseColor("#B9B3C9"))
            textSize = 12f
            gravity = Gravity.CENTER
        }
        val msg = TextView(context).apply {
            setTextColor(Color.parseColor("#8E87A3"))
            textSize = 11f
            gravity = Gravity.CENTER
        }

        val violet = Color.parseColor("#7C4DFF")
        val gris = Color.parseColor("#3A3350")
        val rouge = Color.parseColor("#B3261E")

        layout.addView(count)
        layout.addView(state)
        layout.addView(msg)
        layout.addView(
            row(
                button("Fermé", violet, CaptureService.ACTION_CALIB_CLOSED),
                button("Ouvert", violet, CaptureService.ACTION_CALIB_OPEN)
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8) }
        )
        layout.addView(
            row(
                button("-1", gris, CaptureService.ACTION_UNDO),
                button("Effacer", gris, CaptureService.ACTION_CLEAR_CALIB),
                button("Stop", rouge, CaptureService.ACTION_STOP)
            ),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(6) }
        )

        val p = WindowManager.LayoutParams(
            dp(240),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(16)
            y = dp(80)
        }

        // Déplacer la bulle en tirant sur le compteur
        var startX = 0
        var startY = 0
        var touchX = 0f
        var touchY = 0f
        count.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = p.x
                    startY = p.y
                    touchX = e.rawX
                    touchY = e.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    p.x = startX + (e.rawX - touchX).toInt()
                    p.y = startY + (e.rawY - touchY).toInt()
                    try {
                        wm.updateViewLayout(layout, p)
                    } catch (_: Exception) {
                    }
                    true
                }
                else -> false
            }
        }

        try {
            wm.addView(layout, p)
            root = layout
            params = p
            countView = count
            stateView = state
            msgView = msg
        } catch (e: Exception) {
            // Permission "Afficher par-dessus les autres applis" manquante
            root = null
        }
    }

    fun update(total: Int, obs: Observation, ready: Boolean, message: String) {
        countView?.text = "$total / $GOAL"
        stateView?.text = when {
            !ready -> "Calibration requise"
            obs == Observation.OPEN -> "Vu : coffre ouvert"
            obs == Observation.CLOSED -> "Vu : coffre fermé"
            else -> "Vu : rien"
        }
        msgView?.text = message
    }

    fun hide() {
        root?.let {
            try {
                wm.removeView(it)
            } catch (_: Exception) {
            }
        }
        root = null
        params = null
        countView = null
        stateView = null
        msgView = null
    }
}
