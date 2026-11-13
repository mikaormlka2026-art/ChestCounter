package com.chestcounter.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.chestcounter.detect.TemplateKind
import com.chestcounter.ui.components.ScreenColumn
import com.chestcounter.ui.components.SecondaryButton
import com.chestcounter.ui.components.SectionCard
import com.chestcounter.ui.components.Stepper
import com.chestcounter.ui.theme.Palette
import com.chestcounter.ui.theme.Spacing

@Composable
fun CalibrationScreen(
    running: Boolean,
    countdown: String,
    onSend: (TemplateKind, Float, Float, Float, Float) -> Unit
) {
    var zl by rememberSaveable { mutableStateOf(35) }
    var zt by rememberSaveable { mutableStateOf(55) }
    var zr by rememberSaveable { mutableStateOf(65) }
    var zb by rememberSaveable { mutableStateOf(75) }

    val busy = countdown.isNotEmpty()

    fun send(kind: TemplateKind) {
        onSend(kind, zl / 100f, zt / 100f, zr / 100f, zb / 100f)
    }

    ScreenColumn {
        Text(
            "CALIBRATION",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim
        )

        SectionCard("Zone du coffre") {
            Text(
                "Règle la zone en pourcentage de l'écran, puis touche un bouton " +
                    "et va sur GeForce NOW : l'image est prise après 5 secondes.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim
            )
            Spacer(Modifier.height(Spacing.m))
            ZonePreview(zl, zt, zr, zb)
            Spacer(Modifier.height(Spacing.m))
            Stepper("Gauche", zl, { zl = it.coerceIn(0, zr - 5) })
            Stepper("Haut", zt, { zt = it.coerceIn(0, zb - 5) })
            Stepper("Droite", zr, { zr = it.coerceIn(zl + 5, 100) })
            Stepper("Bas", zb, { zb = it.coerceIn(zt + 5, 100) })
        }

        SectionCard("Enregistrer un modèle") {
            if (!running) {
                Text(
                    "Démarre d'abord la capture depuis l'accueil.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.Danger
                )
                Spacer(Modifier.height(Spacing.s))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SecondaryButton(
                    "Modèle fermé",
                    { send(TemplateKind.CLOSED) },
                    Modifier.weight(1f),
                    enabled = running && !busy
                )
                SecondaryButton(
                    "Modèle ouvert",
                    { send(TemplateKind.OPEN) },
                    Modifier.weight(1f),
                    enabled = running && !busy
                )
            }
            if (busy) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    countdown,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.PrimaryLight
                )
            }
        }
    }
}

@Composable
private fun ZonePreview(left: Int, top: Int, right: Int, bottom: Int) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Palette.Navy)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val x = size.width * left / 100f
            val y = size.height * top / 100f
            val w = size.width * (right - left) / 100f
            val h = size.height * (bottom - top) / 100f
            drawRect(
                color = Palette.Gold.copy(alpha = 0.18f),
                topLeft = Offset(x, y),
                size = Size(w, h)
            )
            drawRect(
                color = Palette.Gold,
                topLeft = Offset(x, y),
                size = Size(w, h),
                style = Stroke(width = 3.dp.toPx())
            )
        }
    }
}
