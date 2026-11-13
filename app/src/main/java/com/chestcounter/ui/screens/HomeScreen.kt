package com.chestcounter.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.chestcounter.capture.CaptureStatus
import com.chestcounter.data.AnalysisOutcome
import com.chestcounter.data.AnalysisResult
import com.chestcounter.data.ChestGoal
import com.chestcounter.data.DayCount
import com.chestcounter.data.SessionAnalysisRunner
import com.chestcounter.ui.components.AnimatedCounter
import com.chestcounter.ui.components.DayItem
import com.chestcounter.ui.components.DayStrip
import com.chestcounter.ui.components.GlassCard
import com.chestcounter.ui.components.GlassTile
import com.chestcounter.ui.components.GlassTone
import com.chestcounter.ui.components.PrimaryButton
import com.chestcounter.ui.components.ScreenColumn
import com.chestcounter.ui.components.SecondaryButton
import com.chestcounter.ui.components.SectionCard
import com.chestcounter.ui.components.StatusPill
import com.chestcounter.ui.components.TrendChart
import com.chestcounter.ui.theme.Palette
import com.chestcounter.ui.theme.Spacing
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

@Composable
fun HomeScreen(
    total: Int,
    today: Int,
    status: CaptureStatus,
    overlayOk: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onGrantOverlay: () -> Unit,
    daily: List<DayCount> = emptyList()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val analyzing = remember { mutableStateOf(false) }
    val outcome = remember { mutableStateOf<AnalysisOutcome?>(null) }
    val info = remember { mutableStateOf("") }
    var selected by rememberSaveable { mutableStateOf(6) }

    outcome.value?.let { o ->
        SessionReportDialog(o) { outcome.value = null }
    }

    val days = remember(daily) { buildDays(daily) }
    val sel = selected.coerceIn(0, days.lastIndex)
    val selLabel = if (sel == days.lastIndex) {
        "aujourd'hui"
    } else {
        "${days[sel].weekday.lowercase()} ${days[sel].number}"
    }
    val detection = when {
        !status.running -> "Arrêtée"
        !status.hasTemplates -> "À calibrer"
        else -> "Active"
    }

    ScreenColumn {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "CHEST COUNTER",
                    style = MaterialTheme.typography.labelSmall,
                    color = Palette.TextDim
                )
                Text("Accueil", style = MaterialTheme.typography.titleLarge)
            }
            StatusPill(
                if (status.running) "Capture active" else "Capture arrêtée",
                if (status.running) Palette.Success else Palette.Danger
            )
        }

        DayStrip(days, sel, { selected = it })

        GlassCard {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        "COFFRES OUVERTS",
                        style = MaterialTheme.typography.labelSmall,
                        color = Palette.TextDim
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        AnimatedCounter(total)
                        Spacer(Modifier.width(Spacing.s))
                        Text(
                            "/ ${ChestGoal.TARGET}",
                            style = MaterialTheme.typography.bodyLarge,
                            color = Palette.TextDim,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x80FFFFFF))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "${max(0, ChestGoal.TARGET - total)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "restants",
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            GoalBar(total.toFloat() / ChestGoal.TARGET)
        }

        GlassCard(contentPadding = PaddingValues(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Coffres par jour", style = MaterialTheme.typography.labelLarge)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("${days[sel].count}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        selLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.TextDim,
                        modifier = Modifier.padding(bottom = 3.dp)
                    )
                }
            }
            Spacer(Modifier.height(Spacing.s))
            TrendChart(days.map { it.count }, sel, { selected = it })
        }

        GlassCard(tone = GlassTone.NAVY, contentPadding = PaddingValues(14.dp)) {
            Text("État de la capture", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTile("Total des coffres", "$total", Modifier.weight(1f))
                GlassTile("Aujourd'hui", "$today", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GlassTile("Session", "${status.sessionCount}", Modifier.weight(1f))
                GlassTile("Détection", detection, Modifier.weight(1f))
            }
        }

        SectionCard("Capture") {
            if (status.running) {
                SecondaryButton("Arrêter la capture", onStop, Modifier.fillMaxWidth())
            } else {
                PrimaryButton("Démarrer la capture", onStart, Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(Spacing.s))
            SecondaryButton(
                if (analyzing.value) "Analyse en cours…" else "Partie terminée",
                {
                    if (!analyzing.value) {
                        val wasRunning = status.running
                        if (wasRunning) onStop()
                        analyzing.value = true
                        info.value = ""
                        scope.launch {
                            val res = SessionAnalysisRunner.analyzeLatest(context, wasRunning)
                            analyzing.value = false
                            when (res) {
                                is AnalysisResult.Done -> outcome.value = res.outcome
                                is AnalysisResult.NoSession -> info.value = "Aucune session enregistrée."
                                is AnalysisResult.Failed -> info.value = "Analyse impossible : ${res.message}"
                            }
                        }
                    }
                },
                Modifier.fillMaxWidth(),
                enabled = !analyzing.value
            )
            if (info.value.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.s))
                Text(info.value, style = MaterialTheme.typography.bodyMedium, color = Palette.Danger)
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
private fun GoalBar(fraction: Float) {
    val f by animateFloatAsState(
        targetValue = fraction.coerceIn(0f, 1f),
        animationSpec = tween(700),
        label = "goal"
    )
    Column(
        Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0x8CFFFFFF))
    ) {
        Column(
            Modifier
                .fillMaxWidth(f)
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(Palette.AccentBrush)
        ) {}
    }
}

/** Les 7 derniers jours, aujourd'hui en dernier, avec le vrai nombre de coffres de Room. */
private fun buildDays(daily: List<DayCount>): List<DayItem> {
    val counts = daily.associate { it.day to it.n }
    val keyFmt = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -6)
    val out = ArrayList<DayItem>(7)
    for (i in 0 until 7) {
        val d = cal.time
        out.add(
            DayItem(
                weekday = dayFmt.format(d).replace(".", "").take(3).uppercase(Locale.getDefault()),
                number = cal.get(Calendar.DAY_OF_MONTH).toString(),
                count = counts[keyFmt.format(d)] ?: 0
            )
        )
        cal.add(Calendar.DAY_OF_YEAR, 1)
    }
    return out
}
