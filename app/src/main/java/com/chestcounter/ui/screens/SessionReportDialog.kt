package com.chestcounter.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.chestcounter.analysis.Verdict
import com.chestcounter.data.AnalysisOutcome
import com.chestcounter.ui.theme.Palette
import java.util.Locale
import kotlin.math.roundToInt

private fun verdictLabel(v: Verdict): String = when (v) {
    Verdict.CONFIRMED_USER -> "Confirmé (toi)"
    Verdict.PROBABLE_USER -> "Probable (toi)"
    Verdict.OPENED_BY_OTHER -> "Autre joueur"
    Verdict.UNKNOWN_ATTRIBUTION -> "Auteur inconnu"
    Verdict.ALREADY_OPEN_ON_FIRST_OBSERVATION -> "Déjà ouvert"
    Verdict.NOT_OPENED -> "Non ouvert"
    Verdict.INVALID_EVENT -> "Rejeté"
}

@Composable
fun SessionReportDialog(outcome: AnalysisOutcome, onDismiss: () -> Unit) {
    val r = outcome.report
    val observed = r.confirmed + r.probable + r.openedByOthers + r.unknown
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Palette.SurfaceHigh,
        titleContentColor = Palette.TextPrimary,
        textContentColor = Palette.TextPrimary,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Fermer") } },
        title = { Text("Bilan de la partie") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Minimum certain : ${r.minimum}", style = MaterialTheme.typography.titleMedium)
                Text("Possible : entre ${r.minimum} et ${r.maximum}", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Ouvertures observées : $observed (auteur à établir)",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.TextDim
                )
                Spacer(Modifier.height(12.dp))
                Text("Coffres confirmés ouverts par toi : ${r.confirmed}")
                Text("Probablement ouverts par toi : ${r.probable}")
                Text("Ouverts par d'autres joueurs : ${r.openedByOthers}")
                Text("Déjà ouverts à la première observation : ${r.alreadyOpen}")
                Text("Auteur inconnu : ${r.unknown}")
                Text("Événements rejetés : ${r.rejected}")
                Text("Joueur identifiable : " + (r.coverage?.let { "${(it * 100).roundToInt()} %" } ?: "—"))
                Text("Session : " + if (r.sessionComplete) "complète" else "incomplète")
                if (r.uncertaintyCauses.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Causes d'incertitude", style = MaterialTheme.typography.titleSmall)
                    for (c in r.uncertaintyCauses) {
                        Text("• $c", style = MaterialTheme.typography.bodySmall, color = Palette.TextDim)
                    }
                }
                if (r.events.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("Détail", style = MaterialTheme.typography.titleSmall)
                    for (e in r.events.take(30)) {
                        val sec = ((e.recon.firstOpenMs ?: outcome.sessionStartMonoMs) - outcome.sessionStartMonoMs) / 1000.0
                        Text(
                            "Coffre #${e.recon.trackId} · t+" + String.format(Locale.ROOT, "%.1f", sec) +
                                " s · " + verdictLabel(e.verdict),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            e.reason,
                            style = MaterialTheme.typography.bodySmall,
                            color = Palette.TextDim
                        )
                    }
                    if (r.events.size > 30) {
                        Text("… et ${r.events.size - 30} autres", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    )
}
