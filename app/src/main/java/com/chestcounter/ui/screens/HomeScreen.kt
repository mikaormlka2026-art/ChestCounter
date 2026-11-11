package com.chestcounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chestcounter.capture.CaptureStatus
import com.chestcounter.data.ChestGoal
import com.chestcounter.ui.components.MetricTile
import com.chestcounter.ui.components.PrimaryButton
import com.chestcounter.ui.components.ScreenColumn
import com.chestcounter.ui.components.SecondaryButton
import com.chestcounter.ui.components.SectionCard
import com.chestcounter.ui.components.StatusPill
import com.chestcounter.ui.theme.Palette
import com.chestcounter.ui.theme.Spacing
import kotlin.math.max

@Composable
fun HomeScreen(
    total: Int,
    today: Int,
    status: CaptureStatus,
    overlayOk: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGrantOverlay: () -> Unit
) {
    ScreenColumn {
        Text(
            "CHEST COUNTER",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim
        )

        SectionCard("Progression") {
            ProgressRingCounter(total)
            Spacer(Modifier.height(Spacing.l))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                MetricTile("Aujourd'hui", "$today", Modifier.weight(1f))
                MetricTile("Session", "${status.sessionCount}", Modifier.weight(1f))
                MetricTile(
                    "Restants",
                    "${max(0, ChestGoal.TARGET - total)}",
                    Modifier.weight(1f)
                )
            }
        }

        SectionCard("Capture") {
            StatusPill(
                if (status.running) "En cours" else "Arrêtée",
                if (status.running) Palette.Success else Palette.Danger
            )
            Spacer(Modifier.height(Spacing.m))
            if (status.running) {
                SecondaryButton("Arrêter la capture", onStop, Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Démarrer la capture", onStart, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.s))
            if (overlayOk) {
                Text(
                    "Bulle flottante autorisée",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Success
                )
            } else {
                SecondaryButton(
                    "Autoriser la bulle flottante",
                    onGrantOverlay,
                    Modifier.fillMaxWidth()
                )
            }
            if (status.message.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.PrimaryLight
                )
            }
        }
    }
}

@Composable
private fun ColumnScopeRing(total: Int) {
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.ProgressRingCounter(total: Int) {
    com.chestcounter.ui.components.ProgressRing(
        fraction = total.toFloat() / ChestGoal.TARGET,
        modifier = Modifier
            .size(200.dp)
            .align(Alignment.CenterHorizontally)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$total", style = MaterialTheme.typography.displayLarge)
            Text(
                "/ ${ChestGoal.TARGET}",
                style = MaterialTheme.typography.bodyLarge,
                color = Palette.TextDim
            )
        }
    }
}
