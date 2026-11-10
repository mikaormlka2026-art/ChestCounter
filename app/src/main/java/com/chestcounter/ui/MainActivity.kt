package com.chestcounter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chestcounter.capture.CaptureService
import com.chestcounter.data.AppDb
import com.chestcounter.data.CaptureCollector
import com.chestcounter.data.ChestEvent
import com.chestcounter.data.ChestGoal
import com.chestcounter.detect.TemplateKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.min

private val Bg = Color(0xFF0E0B1F)
private val CardBg = Color(0xFF1A1438)
private val Violet = Color(0xFF8E5BFF)
private val VioletLight = Color(0xFFE0B3FF)
private val Dim = Color(0xFFA99AD6)
private val Ok = Color(0xFF9CFFD9)
private val Warn = Color(0xFFFF8A9B)

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { Screen() }
    }

    override fun onResume() {
        super.onResume()
        resumeTick += 1
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }

    private fun startOfDay(): Long {
        val c = Calendar.getInstance()
        c.set(Calendar.HOUR_OF_DAY, 0)
        c.set(Calendar.MINUTE, 0)
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }

    @Composable
    private fun Screen() {
        val scope = rememberCoroutineScope()
        val dao = remember { AppDb.get(applicationContext).chestDao() }
        val status by CaptureService.status.collectAsState()
        val total by remember { dao.observeTotal() }.collectAsState(initial = 0)
        val today by remember { dao.observeSince(startOfDay()) }.collectAsState(initial = 0)
        val recent by remember { dao.observeRecent(8) }
            .collectAsState(initial = emptyList<ChestEvent>())
        val overlayOk = remember(resumeTick) { Settings.canDrawOverlays(this@MainActivity) }

        var zl by remember { mutableStateOf(35) }
        var zt by remember { mutableStateOf(55) }
        var zr by remember { mutableStateOf(65) }
        var zb by remember { mutableStateOf(75) }
        var countdown by remember { mutableStateOf("") }
        var confirmAll by remember { mutableStateOf(false) }
        var collectOn by remember {
            mutableStateOf(CaptureCollector.isEnabled(applicationContext))
        }

        val notifLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { }

        val projectionLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { r ->
            val data = r.data
            if (r.resultCode == RESULT_OK && data != null) {
                try {
                    ContextCompat.startForegroundService(
                        this@MainActivity,
                        CaptureService.startIntent(this@MainActivity, r.resultCode, data)
                    )
                } catch (t: Throwable) {
                    toast("Démarrage impossible : ${t.message}")
                }
            } else {
                toast("Autorisation de capture refusée")
            }
        }

        fun startCapture() {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(
                    this@MainActivity, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            val mpm = getSystemService(MediaProjectionManager::class.java)
            projectionLauncher.launch(mpm.createScreenCaptureIntent())
        }

        fun stopCapture() {
            try {
                startService(CaptureService.stopIntent(this@MainActivity))
            } catch (t: Throwable) {
                toast("Arrêt impossible : ${t.message}")
            }
        }

        fun sendTemplate(kind: TemplateKind) {
            if (!status.running) {
                toast("Démarre d'abord la capture")
                return
            }
            if (countdown.isNotEmpty()) return
            scope.launch {
                for (i in 5 downTo 1) {
                    countdown = "Enregistrement dans $i s… va sur GeForce NOW"
                    delay(1000)
                }
                countdown = ""
                try {
                    startService(
                        CaptureService.templateIntent(
                            this@MainActivity, kind,
                            zl / 100f, zt / 100f, zr / 100f, zb / 100f
                        )
                    )
                } catch (t: Throwable) {
                    toast("Impossible d'envoyer : ${t.message}")
                }
            }
        }

        val closedKind = TemplateKind.CLOSED
        val openKind = TemplateKind.values().firstOrNull { it != TemplateKind.CLOSED }
            ?: TemplateKind.CLOSED

        Column(
            Modifier
                .fillMaxSize()
                .background(Bg)
                .systemBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            T("CHEST COUNTER", 12, Dim, true)

            Card("Progression") {
                Row(verticalAlignment = Alignment.Bottom) {
                    T("$total", 48, Color.White, true)
                    T("  / ${ChestGoal.TARGET}", 18, Dim, modifier = Modifier.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
                val frac = min(1f, total.toFloat() / ChestGoal.TARGET)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0x33FFFFFF))
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(frac)
                            .fillMaxHeight()
                            .background(Brush.horizontalGradient(listOf(Violet, VioletLight)))
                    )
                }
                Spacer(Modifier.height(8.dp))
                T("Aujourd'hui : $today   ·   Cette session : ${status.sessionCount}", 13, Dim)
            }

            Card("Capture") {
                T(
                    if (status.running) "En cours" else "Arrêtée",
                    18,
                    if (status.running) Ok else Warn,
                    true
                )
                Spacer(Modifier.height(10.dp))
                if (status.running) {
                    Btn("Arrêter la capture", Modifier.fillMaxWidth(), primary = false) { stopCapture() }
                } else {
                    Btn("Démarrer la capture", Modifier.fillMaxWidth()) { startCapture() }
                }
                if (!overlayOk) {
                    Spacer(Modifier.height(8.dp))
                    Btn("Autoriser la bulle flottante", Modifier.fillMaxWidth(), primary = false) {
                        try {
                            startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:$packageName")
                                )
                            )
                        } catch (t: Throwable) {
                            toast("Réglage introuvable")
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    T("Bulle flottante autorisée", 12, Ok)
                }
            }

            Card("Diagnostic") {
                T("Modèles : ${if (status.hasTemplates) "présents" else "à calibrer"}", 13)
                T("Score fermé : ${(status.closedScore * 100).toInt()} %", 13)
                T("Score ouvert : ${(status.openScore * 100).toInt()} %", 13)
                T("Temps de traitement : ${status.processingMs} ms", 13)
                T("Images analysées : ${status.framesProcessed}", 13)
                T("État : ${status.machineState}", 13)
                T("Dernière confiance : ${(status.lastConfidence * 100).toInt()} %", 13)
                T("Erreurs : ${status.errorCount}", 13, if (status.errorCount > 0) Warn else Color.White)
                if (status.message.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    T(status.message, 13, VioletLight)
                }
            }

            Card("Collecte de captures") {
                T(
                    if (collectOn) "Activée" else "Désactivée",
                    16,
                    if (collectOn) Ok else Dim,
                    true
                )
                Spacer(Modifier.height(6.dp))
                T(
                    "Enregistre des images de tout l'écran dans Pictures/ChestCounter " +
                        "(coffre compté, cas ambigus, une image de fond toutes les 15 s). " +
                        "Maximum 300 par session. Classement provisoire, à vérifier à la main.",
                    12, Dim
                )
                Spacer(Modifier.height(8.dp))
                Btn(
                    if (collectOn) "Désactiver la collecte" else "Activer la collecte",
                    Modifier.fillMaxWidth(),
                    primary = !collectOn
                ) {
                    collectOn = !collectOn
                    CaptureCollector.setEnabled(applicationContext, collectOn)
                }
                Spacer(Modifier.height(8.dp))
                T("Images enregistrées cette session : ${status.savedCaptures}", 13)
                if (status.collectorError.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    T("Erreur de collecte : ${status.collectorError}", 12, Warn)
                }
                Spacer(Modifier.height(6.dp))
                T(
                    "Attention : tout ce qui est à l'écran est enregistré. " +
                        "Active la collecte seulement pendant que tu joues.",
                    11, Warn
                )
            }

            Card("Calibration") {
                T(
                    "Règle la zone (en % de l'écran), touche un bouton, puis va sur " +
                        "GeForce NOW : l'image est prise après 5 secondes.",
                    12, Dim
                )
                Spacer(Modifier.height(8.dp))
                Stepper("Gauche", zl, { zl = it.coerceIn(0, zr - 5) })
                Stepper("Haut", zt, { zt = it.coerceIn(0, zb - 5) })
                Stepper("Droite", zr, { zr = it.coerceIn(zl + 5, 100) })
                Stepper("Bas", zb, { zb = it.coerceIn(zt + 5, 100) })
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Modèle fermé", Modifier.weightFill(), primary = false) { sendTemplate(closedKind) }
                    Btn("Modèle ouvert", Modifier.weightFill(), primary = false) { sendTemplate(openKind) }
                }
                if (countdown.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    T(countdown, 13, VioletLight)
                }
            }

            Card("Derniers coffres") {
                if (recent.isEmpty()) {
                    T("Aucun coffre compté", 13, Dim)
                } else {
                    val fmt = SimpleDateFormat("dd/MM HH:mm:ss", Locale.getDefault())
                    recent.forEach { e ->
                        T("${fmt.format(Date(e.timestampMs))}  ·  confiance ${(e.confidence * 100).toInt()} %", 13)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Btn("Annuler le dernier", Modifier.weightFill(), primary = false) {
                        scope.launch(Dispatchers.IO) { dao.deleteLast() }
                    }
                    Btn(
                        if (confirmAll) "Confirmer ?" else "Tout effacer",
                        Modifier.weightFill(),
                        primary = false
                    ) {
                        if (confirmAll) {
                            confirmAll = false
                            scope.launch(Dispatchers.IO) { dao.deleteAll() }
                        } else {
                            confirmAll = true
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun T(
    text: String,
    size: Int = 14,
    color: Color = Color.White,
    bold: Boolean = false,
    modifier: Modifier = Modifier
) {
    BasicText(
        text,
        modifier,
        style = TextStyle(
            color = color,
            fontSize = size.sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
        )
    )
}

@Composable
private fun Btn(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = true,
    onClick: () -> Unit
) {
    val brush = if (primary) {
        Brush.horizontalGradient(listOf(Violet, Color(0xFFB57BFF)))
    } else {
        Brush.horizontalGradient(listOf(Color(0xFF2A2250), Color(0xFF2A2250)))
    }
    Box(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(brush)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        T(label, 14, Color.White, true)
    }
}

@Composable
private fun Card(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(CardBg)
            .padding(16.dp)
    ) {
        T(title.uppercase(), 11, Dim, true)
        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
private fun Stepper(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        T(label, 13, Color.White, modifier = Modifier.width(70.dp))
        Btn("−", primary = false) { onChange(value - 5) }
        T("$value %", 14, Color.White, true, Modifier.width(64.dp).padding(start = 12.dp))
        Btn("+", primary = false) { onChange(value + 5) }
    }
}

private fun Modifier.weightFill(): Modifier = this.then(Modifier.width(150.dp))
