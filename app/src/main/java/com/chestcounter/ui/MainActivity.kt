package com.chestcounter.ui

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chestcounter.capture.CaptureService
import com.chestcounter.data.AppDb
import com.chestcounter.data.GOAL
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val Violet = Color(0xFF7C4DFF)
private val VioletClair = Color(0xFFC4A7FF)
private val Fond = Color(0xFF0F0B1A)
private val Carte = Color(0xFF1A1426)
private val TexteDoux = Color(0xFFB9B3C9)

class MainActivity : ComponentActivity() {

    private val projectionLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data = result.data
            if (result.resultCode == Activity.RESULT_OK && data != null) {
                val intent = Intent(this, CaptureService::class.java).apply {
                    action = CaptureService.ACTION_START
                    putExtra(CaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(CaptureService.EXTRA_DATA, data)
                }
                startForegroundService(intent)
            } else {
                CaptureService.status.value = "Autorisation de capture refusée"
            }
        }

    private val notifLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            // Même si la notification est refusée, la capture peut démarrer.
            launchProjection()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Violet,
                    background = Fond,
                    surface = Carte,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                Surface(modifier = Modifier.fillMaxSize(), color = Fond) {
                    Dashboard(onStart = ::startCapture, onStop = ::stopCapture)
                }
            }
        }
    }

    private fun startCapture() {
        if (!Settings.canDrawOverlays(this)) {
            CaptureService.status.value =
                "Autorise « Afficher par-dessus les autres applis », puis reviens et touche Démarrer"
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
            return
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            launchProjection()
        }
    }

    private fun launchProjection() {
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(mpm.createScreenCaptureIntent())
    }

    private fun stopCapture() {
        startService(Intent(this, CaptureService::class.java).apply {
            action = CaptureService.ACTION_STOP
        })
    }
}

private fun startOfToday(): Long =
    Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

@Composable
private fun Dashboard(onStart: () -> Unit, onStop: () -> Unit) {
    val ctx = LocalContext.current
    val dao = remember { AppDb.get(ctx).dao() }
    val scope = rememberCoroutineScope()

    val total by remember { dao.totalFlow() }.collectAsState(initial = 0)
    val today by remember { dao.countSinceFlow(startOfToday()) }.collectAsState(initial = 0)
    val last by remember { dao.lastFlow() }.collectAsState(initial = null)
    val running by CaptureService.running.collectAsState()
    val status by CaptureService.status.collectAsState()

    var confirmReset by remember { mutableStateOf(false) }

    val progress = (total.toFloat() / GOAL).coerceIn(0f, 1f)
    val lastText = last?.let {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(it))
    } ?: "—"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Chest Counter", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = VioletClair)

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = Carte),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("Coffres ouverts", color = TexteDoux, fontSize = 14.sp)
                Spacer(Modifier.height(4.dp))
                Text("$total / $GOAL", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = Color.White)
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = Violet,
                    trackColor = Color(0xFF2E2644)
                )
                Spacer(Modifier.height(6.dp))
                Text("${(progress * 100).toInt()} %", color = TexteDoux, fontSize = 13.sp)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatCard("Aujourd'hui", today.toString(), Modifier.weight(1f))
            StatCard("Dernier", lastText, Modifier.weight(1f))
            StatCard("Restants", (GOAL - total).coerceAtLeast(0).toString(), Modifier.weight(1f))
        }

        if (running) {
            Button(
                onClick = onStop,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB3261E))
            ) { Text("Arrêter la capture", fontSize = 16.sp) }
        } else {
            Button(
                onClick = onStart,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Violet)
            ) { Text("Démarrer", fontSize = 16.sp) }
        }

        if (status.isNotEmpty()) {
            Text(status, color = TexteDoux, fontSize = 13.sp)
        }

        Text(
            "1. Démarrer, puis choisis « Écran entier » si Android te le propose.\n" +
                "2. Ouvre GeForce NOW : la bulle apparaît par-dessus.\n" +
                "3. Place un coffre fermé au centre, touche « Fermé ». Fais pareil avec un coffre ouvert et « Ouvert ».",
            color = TexteDoux,
            fontSize = 13.sp,
            lineHeight = 19.sp
        )

        OutlinedButton(
            onClick = { confirmReset = true },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Remettre le compteur à zéro", color = TexteDoux) }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Remettre à zéro ?") },
            text = { Text("Tous les coffres comptés seront effacés. Impossible d'annuler.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    scope.launch { dao.deleteAll() }
                }) { Text("Effacer", color = Color(0xFFFF6B6B)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Annuler") }
            }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Carte),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = TexteDoux, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}
