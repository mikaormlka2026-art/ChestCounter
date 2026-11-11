package com.chestcounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.chestcounter.data.ChestEvent
import com.chestcounter.ui.components.KeyValue
import com.chestcounter.ui.components.ScreenColumn
import com.chestcounter.ui.components.SecondaryButton
import com.chestcounter.ui.components.SectionCard
import com.chestcounter.ui.theme.Palette
import com.chestcounter.ui.theme.Spacing
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HistoryScreen(
    recent: List<ChestEvent>,
    total: Int,
    onUndoLast: () -> Unit,
    onClearAll: () -> Unit
) {
    var confirmAll by rememberSaveable { mutableStateOf(false) }

    ScreenColumn {
        Text(
            "HISTORIQUE",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim
        )

        SectionCard("Derniers coffres") {
            Text(
                "$total au total",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(Spacing.s))
            if (recent.isEmpty()) {
                Text(
                    "Aucun coffre compté",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.TextDim
                )
            } else {
                val fmt = SimpleDateFormat("dd/MM  HH:mm:ss", Locale.getDefault())
                recent.forEach { e ->
                    KeyValue(
                        fmt.format(Date(e.timestampMs)),
                        "${(e.confidence * 100).toInt()} %"
                    )
                }
            }
        }

        SectionCard("Corrections") {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.s)) {
                SecondaryButton(
                    "Annuler le dernier",
                    onUndoLast,
                    Modifier.weight(1f),
                    enabled = recent.isNotEmpty()
                )
                SecondaryButton(
                    if (confirmAll) "Confirmer ?" else "Tout effacer",
                    {
                        if (confirmAll) {
                            confirmAll = false
                            onClearAll()
                        } else {
                            confirmAll = true
                        }
                    },
                    Modifier.weight(1f),
                    enabled = recent.isNotEmpty(),
                    contentColor = if (confirmAll) Palette.Danger else Palette.TextPrimary
                )
            }
            if (confirmAll) {
                Spacer(Modifier.height(Spacing.s))
                Text(
                    "Tous les coffres comptés seront supprimés, sans retour possible.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Danger
                )
                Spacer(Modifier.height(Spacing.s))
                SecondaryButton("Annuler", { confirmAll = false })
            }
        }
    }
}
