package com.chestcounter.ui

import android.Manifest
import android.content.Context
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.chestcounter.capture.CaptureService
import com.chestcounter.data.AppDb
import com.chestcounter.data.CaptureCollector
import com.chestcounter.data.ChestEvent
import com.chestcounter.detect.TemplateKind
import com.chestcounter.ui.screens.CalibrationScreen
import com.chestcounter.ui.screens.DiagnosticScreen
import com.chestcounter.ui.screens.HistoryScreen
import com.chestcounter.ui.screens.HomeScreen
import com.chestcounter.ui.screens.SettingsScreen
import com.chestcounter.ui.theme.ChestTheme
import com.chestcounter.ui.theme.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

private enum class Tab(val label: String, val glyph: String) {
    HOME("Accueil", "⌂"),
    CALIBRATION("Calibration", "◎"),
    DIAGNOSTIC("Diagnostic", "◈"),
    HISTORY("Historique", "≡"),
    SETTINGS("Réglages", "⚙")
}

private val NoInsets = WindowInsets(0, 0, 0, 0)

private const val CAPTURE_PREFS = "capture_prefs"
private const val KEY_DIAGNOSTIC = "diagnostic"

class MainActivity : ComponentActivity() {

    private var resumeTick by mutableStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ChestTheme { App() } }
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

    private fun capturePrefs() = getSharedPreferences(CAPTURE_PREFS, Context.MODE_PRIVATE)

    @Composable
    private fun App() {
        val scope = rememberCoroutineScope()
        val dao = remember { AppDb.get(applicationContext).chestDao() }
        val status by CaptureService.status.collectAsState()
        val total by remember { dao.observeTotal() }.collectAsState(initial = 0)
        val today by remember { dao.observeSince(startOfDay()) }.collectAsState(initial = 0)
        val recent by remember { dao.observeRecent(50) }
            .collectAsState(initial = emptyList<ChestEvent>())
        val overlayOk = remember(resumeTick) { Settings.canDrawOverlays(this@MainActivity) }

        var tabIndex by rememberSaveable { mutableStateOf(0) }
        var countdown by remember { mutableStateOf("") }
        var collectOn by remember {
            mutableStateOf(CaptureCollector.isEnabled(applicationContext))
        }
        var diagOn by remember(resumeTick) {
            mutableStateOf(capturePrefs().getBoolean(KEY_DIAGNOSTIC, false))
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

        fun grantOverlay() {
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

        fun toggleDiagnostic() {
            if (!overlayOk) {
                toast("Autorise d'abord l'affichage par-dessus les applis")
                return
            }
            if (status.running) {
                try {
                    startService(CaptureService.toggleDiagnosticIntent(this@MainActivity))
                    diagOn = !diagOn
                } catch (t: Throwable) {
                    toast("Impossible : ${t.message}")
                }
            } else {
                capturePrefs().edit().putBoolean(KEY_DIAGNOSTIC, !diagOn).apply()
                diagOn = !diagOn
            }
        }

        fun sendTemplate(kind: TemplateKind, l: Float, t: Float, r: Float, b: Float) {
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
                    startService(CaptureService.templateIntent(this@MainActivity, kind, l, t, r, b))
                } catch (e: Throwable) {
                    toast("Impossible d'envoyer : ${e.message}")
                }
            }
        }

        val tabs = Tab.values()
        val current = tabIndex.coerceIn(0, tabs.size - 1)

        val content: @Composable () -> Unit = {
            when (tabs[current]) {
                Tab.HOME -> HomeScreen(
                    total = total,
                    today = today,
                    status = status,
                    overlayOk = overlayOk,
                    onStart = { startCapture() },
                    onStop = { stopCapture() },
                    onGrantOverlay = { grantOverlay() }
                )

                Tab.CALIBRATION -> CalibrationScreen(
                    running = status.running,
                    countdown = countdown,
                    onSend = { k, l, t, r, b -> sendTemplate(k, l, t, r, b) }
                )

                Tab.DIAGNOSTIC -> DiagnosticScreen(
                    status = status,
                    diagnosticOn = diagOn,
                    onToggleDiagnostic = { toggleDiagnostic() }
                )

                Tab.HISTORY -> HistoryScreen(
                    recent = recent,
                    total = total,
                    onUndoLast = { scope.launch(Dispatchers.IO) { dao.deleteLast() } },
                    onClearAll = { scope.launch(Dispatchers.IO) { dao.deleteAll() } }
                )

                Tab.SETTINGS -> SettingsScreen(
                    collectOn = collectOn,
                    onCollectChange = {
                        collectOn = it
                        CaptureCollector.setEnabled(applicationContext, it)
                    },
                    savedCaptures = status.savedCaptures,
                    collectorError = status.collectorError,
                    overlayOk = overlayOk,
                    onGrantOverlay = { grantOverlay() }
                )
            }
        }

        val barColors = NavigationBarItemDefaults.colors(
            selectedIconColor = Palette.TextPrimary,
            selectedTextColor = Palette.TextPrimary,
            indicatorColor = Palette.PrimaryContainer,
            unselectedIconColor = Palette.TextDim,
            unselectedTextColor = Palette.TextDim
        )
        val railColors = NavigationRailItemDefaults.colors(
            selectedIconColor = Palette.TextPrimary,
            selectedTextColor = Palette.TextPrimary,
            indicatorColor = Palette.PrimaryContainer,
            unselectedIconColor = Palette.TextDim,
            unselectedTextColor = Palette.TextDim
        )

        Surface(
            Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            BoxWithConstraints(Modifier.fillMaxSize().systemBarsPadding()) {
                if (maxWidth >= 600.dp) {
                    Row(Modifier.fillMaxSize()) {
                        NavigationRail(
                            containerColor = Palette.Surface,
                            windowInsets = NoInsets
                        ) {
                            tabs.forEachIndexed { i, tab ->
                                NavigationRailItem(
                                    selected = current == i,
                                    onClick = { tabIndex = i },
                                    icon = { Text(tab.glyph, fontSize = 18.sp) },
                                    label = {
                                        Text(
                                            tab.label,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = railColors
                                )
                            }
                        }
                        Box(Modifier.weight(1f).fillMaxHeight()) { content() }
                    }
                } else {
                    Column(Modifier.fillMaxSize()) {
                        Box(Modifier.weight(1f).fillMaxWidth()) { content() }
                        NavigationBar(
                            containerColor = Palette.Surface,
                            windowInsets = NoInsets
                        ) {
                            tabs.forEachIndexed { i, tab ->
                                NavigationBarItem(
                                    selected = current == i,
                                    onClick = { tabIndex = i },
                                    icon = { Text(tab.glyph, fontSize = 18.sp) },
                                    label = {
                                        Text(
                                            tab.label,
                                            fontSize = 11.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    colors = barColors
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
