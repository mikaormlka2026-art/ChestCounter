package com.chestcounter.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chestcounter.data.ChestGoal
import com.chestcounter.ui.components.KeyValue
import com.chestcounter.ui.components.ScreenColumn
import com.chestcounter.ui.components.SecondaryButton
import com.chestcounter.ui.components.SectionCard
import com.chestcounter.ui.theme.Palette
import com.chestcounter.ui.theme.Spacing

@Composable
fun SettingsScreen(
    collectOn: Boolean,
    onCollectChange: (Boolean) -> Unit,
    savedCaptures: Int,
    collectorError: String,
    overlayOk: Boolean,
    onGrantOverlay: () -> Unit
) {
    ScreenColumn {
        Text(
            "RÉGLAGES",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.TextDim
        )

        SectionCard("Collecte de captures") {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (collectOn) "Activée" else "Désactivée",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = if (collectOn) Palette.Success else Palette.TextDim
                )
                Spacer(Modifier.width(Spacing.m))
                Switch(
                    checked = collectOn,
                    onCheckedChange = onCollectChange,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Palette.TextPrimary,
                        checkedTrackColor = Palette.Primary,
                        uncheckedThumbColor = Palette.TextDim,
                        uncheckedTrackColor = Palette.SurfaceHigh
                    )
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Enregistre des images de tout l'écran dans Pictures/ChestCounter " +
                    "(coffre compté, cas ambigus, une image de fond toutes les 15 s). " +
                    "Maximum 300 par session. Classement provisoire, à vérifier à la main.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim
            )
            Spacer(Modifier.height(Spacing.m))
            KeyValue("Images enregistrées cette session", "$savedCaptures")
            if (collectorError.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    "Erreur de collecte : $collectorError",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Danger
                )
            }
            Spacer(Modifier.height(Spacing.s))
            Text(
                "Attention : tout ce qui est à l'écran est enregistré. " +
                    "Active la collecte seulement pendant que tu joues.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Danger
            )
        }

        SectionCard("Affichage par-dessus les applis") {
            Text(
                if (overlayOk) "Autorisé" else "Non autorisé",
                style = MaterialTheme.typography.titleMedium,
                color = if (overlayOk) Palette.Success else Palette.Danger
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                "Nécessaire pour la bulle flottante et les rectangles de diagnostic.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.TextDim
            )
            if (!overlayOk) {
                Spacer(Modifier.height(Spacing.m))
                SecondaryButton("Autoriser", onGrantOverlay, Modifier.fillMaxWidth())
            }
        }

        SectionCard("Objectif") {
            KeyValue("Nombre de coffres visé", "${ChestGoal.TARGET}")
        }
    }
}
