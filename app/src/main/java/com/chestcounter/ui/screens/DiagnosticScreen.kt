package com.chestcounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.chestcounter.capture.CaptureStatus
import com.chestcounter.detect.DetectionKind
import com.chestcounter.ui.components.KeyValue
import com.chestcounter.ui.components.MetricTile
import com.chestcounter.ui.components.PrimaryButton
import com.chestcounter.ui.components.ScreenColumn
import com.chestcounter.ui.components.SecondaryButton
import com.chestcounter.ui.components.SectionCard
import com.chestcounter.ui.theme.Palette
import com.chestcounter.ui.theme.Spacing

@Composable
fun DiagnosticScreen(
    status: CaptureStatus,
    diagnosticOn: Boolean,
    onToggleDiagnostic: () -> Unit
) {
    ScreenColumn {
        Text(
            "DIAGNOSTIC",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim
        )

        SectionCard("Rectangles de détection") {
            Text(
                "Dessine un cadre par-dessus le jeu autour de chaque coffre repéré : " +
                    "doré pour fermé, vert pour ouvert, pointillé quand il n'est plus visible. " +
                    "Le calque ne bloque pas les touches.",
                style = MaterialTheme.typography.bodyMedium,
                color = Palette.TextDim
            )
            Spacer(Modifier.height(Spacing.m))
            if (diagnosticOn) {
                SecondaryButton(
                    "Désactiver les rectangles",
                    onToggleDiagnostic,
                    Modifier.fillMaxWidth()
                )
            } else {
                PrimaryButton(
                    "Activer les rectangles",
                    onToggleDiagnostic,
                    Modifier.fillMaxWidth()
                )
            }
        }

        SectionCard("Capture d'écran") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                MetricTile("Reçues", "${status.framesReceived}", Modifier.weight(1f))
                MetricTile("Noires", "${status.blackFrames}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.m))
            KeyValue(
                "Capture",
                if (status.running) "active" else "arrêtée",
                valueColor = if (status.running) Palette.Success else Palette.Danger
            )
            KeyValue(
                "Dernière image",
                when {
                    status.framesReceived == 0 -> "aucune reçue"
                    status.lastFrameBlack -> "noire"
                    else -> "avec du contenu"
                },
                valueColor = when {
                    status.framesReceived == 0 -> Palette.TextPrimary
                    status.lastFrameBlack -> Palette.Danger
                    else -> Palette.Success
                }
            )
            KeyValue("Taille", "${status.frameWidth} x ${status.frameHeight}")
        }

        SectionCard("Détection") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                MetricTile(
                    "Score fermé",
                    "${(status.closedScore * 100).toInt()} %",
                    Modifier.weight(1f)
                )
                MetricTile(
                    "Score ouvert",
                    "${(status.openScore * 100).toInt()} %",
                    Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                MetricTile("Traitement", "${status.processingMs} ms", Modifier.weight(1f))
                MetricTile("Analysées", "${status.framesProcessed}", Modifier.weight(1f))
            }
            Spacer(Modifier.height(Spacing.m))
            KeyValue(
                "Modèles",
                if (status.hasTemplates) "présents" else "à calibrer",
                valueColor = if (status.hasTemplates) Palette.Success else Palette.Danger
            )
            KeyValue("État", status.machineState)
            KeyValue("Dernière confiance", "${(status.lastConfidence * 100).toInt()} %")
            KeyValue("Coffres suivis", "${status.tracks.size}")
            KeyValue(
                "Erreurs",
                "${status.errorCount}",
                valueColor = if (status.errorCount > 0) Palette.Danger else Palette.TextPrimary
            )
            if (status.message.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.PrimaryLight
                )
            }
        }

        SectionCard("Coffres suivis") {
            if (status.tracks.isEmpty()) {
                Text(
                    "Aucun coffre suivi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim
                )
            } else {
                status.tracks.forEach { t ->
                    val kind = if (t.kind == DetectionKind.CLOSED) "fermé" else "ouvert"
                    val seen = if (t.visible) "visible" else "perdu"
                    KeyValue(
                        "#${t.id}  $kind  $seen",
                        "${(t.score * 100).toInt()} %",
                        valueColor = if (t.kind == DetectionKind.CLOSED) {
                            Palette.Gold
                        } else {
                            Palette.Success
                        }
                    )
                }
            }
        }

        SectionCard("Analyse par candidat") {
            if (status.diagnosticLines.isEmpty()) {
                Text(
                    "Aucune analyse pour l'instant",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim
                )
            } else {
                status.diagnosticLines.forEach { line ->
                    Text(
                        line,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (line.startsWith("#")) Palette.TextPrimary else Palette.TextDim
                    )
                }
            }
        }
    }
}
