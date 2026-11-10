package com.chestcounter.capture

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import com.chestcounter.R
import com.chestcounter.data.AppDb
import com.chestcounter.data.CaptureCollector
import com.chestcounter.data.ChestEvent
import com.chestcounter.data.ChestGoal
import com.chestcounter.detect.ChestDetector
import com.chestcounter.detect.ChestStateMachine
import com.chestcounter.detect.Observation
import com.chestcounter.detect.TemplateKind
import com.chestcounter.ui.Hud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.max
import kotlin.math.min

/** État visible par l'écran principal et le HUD (diagnostic). */
data class CaptureStatus(
    val running: Boolean = false,
    val closedScore: Float = 0f,
    val openScore: Float = 0f,
    val processingMs: Long = 0L,
    val framesProcessed: Int = 0,
    val sessionCount: Int = 0,
    val machineState: String = "IDLE",
    val hasTemplates: Boolean = false,
    val lastConfidence: Float = 0f,
    val errorCount: Int = 0,
    val message: String = "",
    val savedCaptures: Int = 0,
    val collectorError: String = ""
)

class CaptureService : Service() {

    private data class PendingTemplate(
        val kind: TemplateKind,
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float
    )

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var thread: HandlerThread? = null
    private var detector: ChestDetector? = null
    private var collector: CaptureCollector? = null
    private var hud: Hud? = null
    private val machine = ChestStateMachine()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var pending: PendingTemplate? = null
    private var stopping = false
    private var lastProcessAt = 0L
    private var frames = 0
    private var errors = 0

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startCapture(intent)
            ACTION_STOP -> stopCapture()
            ACTION_SAVE_TEMPLATE -> queueTemplate(intent)
            else -> {
                if (projection == null) stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        stopCapture()
        scope.cancel()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- démarrage

    private fun startCapture(intent: Intent) {
        if (projection != null) return
        stopping = false

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = getResultData(intent)
        if (resultCode != Activity.RESULT_OK || data == null) {
            publish { it.copy(running = false, message = "Autorisation de capture manquante") }
            stopSelf()
            return
        }

        createChannel()
        try {
            val notification = buildNotification()
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(
                    NOTIF_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else {
                startForeground(NOTIF_ID, notification)
            }
        } catch (t: Throwable) {
            publish { it.copy(running = false, message = "Service premier plan refusé : ${t.message}") }
            stopSelf()
            return
        }

        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try {
            mpm.getMediaProjection(resultCode, data)
        } catch (t: Throwable) {
            null
        }
        if (mp == null) {
            publish { it.copy(running = false, message = "Impossible d'obtenir la capture d'écran") }
            stopCapture()
            return
        }
        projection = mp

        val ht = HandlerThread("ChestCapture").also { it.start() }
        thread = ht
        val bg = Handler(ht.looper)

        // Obligatoire sur Android 14+ : callback enregistré avant le VirtualDisplay.
        mp.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post { stopCapture() }
            }
        }, bg)

        try {
            val (w, ch, dpi) = captureSize()
            val reader = ImageReader.newInstance(w, ch, PixelFormat.RGBA_8888, 2)
            imageReader = reader
            reader.setOnImageAvailableListener({ r -> onFrame(r) }, bg)
            virtualDisplay = mp.createVirtualDisplay(
                "ChestCounterCapture",
                w, ch, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, bg
            )
        } catch (t: Throwable) {
            publish { it.copy(running = false, message = "Erreur capture : ${t.message}") }
            stopCapture()
            return
        }

        val d = ChestDetector()
        val openCvOk = d.init()
        if (openCvOk) d.loadTemplates(templatesDir())
        detector = d

        collector = CaptureCollector(applicationContext)

        machine.reset()
        frames = 0
        errors = 0
        lastProcessAt = 0L

        publish {
            CaptureStatus(
                running = true,
                hasTemplates = d.hasTemplates(),
                message = when {
                    !openCvOk -> "OpenCV non initialisé"
                    !d.hasTemplates() -> "Calibrer les modèles fermé et ouvert"
                    else -> "Capture démarrée"
                }
            )
        }

        startHud()
    }

    private fun startHud() {
        try {
            if (Settings.canDrawOverlays(this)) {
                hud = Hud(this).also { it.show() }
            }
        } catch (t: Throwable) {
            hud = null
        }
        val dao = AppDb.get(this).chestDao()
        scope.launch {
            combine(status, dao.observeTotal()) { s, total -> hudText(s, total) }
                .distinctUntilChanged()
                .collect { text -> mainHandler.post { hud?.update(text) } }
        }
    }

    private fun hudText(s: CaptureStatus, total: Int): String {
        val closed = (s.closedScore * 100).toInt()
        val open = (s.openScore * 100).toInt()
        return "Coffres $total / ${ChestGoal.TARGET}\n" +
            "F $closed%  O $open%  ${s.processingMs} ms"
    }

    // ------------------------------------------------------------------- images

    private fun onFrame(reader: ImageReader) {
        var image: Image? = null
        try {
            val img = reader.acquireLatestImage() ?: return
            image = img
            val now = SystemClock.elapsedRealtime()
            if (now - lastProcessAt < INTERVAL_MS) return
            lastProcessAt = now

            val bmp = imageToBitmap(img)
            img.close()
            image = null
            try {
                process(bmp, now)
            } finally {
                bmp.recycle()
            }
        } catch (t: Throwable) {
            errors++
            publish { it.copy(errorCount = errors, message = "Erreur : ${t.message}") }
        } finally {
            image?.close()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(plane.buffer)
        if (rowPadding == 0) return padded
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun process(bmp: Bitmap, now: Long) {
        val d = detector ?: return

        val p = pending
        if (p != null) {
            pending = null
            saveTemplate(d, p, bmp)
            return
        }

        if (!d.hasTemplates()) {
            publish { it.copy(hasTemplates = false, message = "Calibrer les modèles fermé et ouvert") }
            return
        }

        val r = d.detect(bmp)
        frames++
        if (r.error != null) {
            errors++
            publish {
                it.copy(
                    processingMs = r.processingMs,
                    framesProcessed = frames,
                    errorCount = errors,
                    message = r.error
                )
            }
            return
        }

        val counted = machine.process(Observation(now, r.closedScore, r.openScore))
        if (counted) {
            val confidence = machine.lastConfidence
            scope.launch {
                try {
                    AppDb.get(applicationContext).chestDao().insert(
                        ChestEvent(timestampMs = System.currentTimeMillis(), confidence = confidence)
                    )
                } catch (t: Throwable) {
                    errors++
                    publish { it.copy(errorCount = errors, message = "Erreur base : ${t.message}") }
                }
            }
        }

        // Collecte facultative de captures (désactivée par défaut).
        val col = collector
        try {
            col?.maybeSave(bmp, now, r.closedScore, r.openScore, counted)
        } catch (t: Throwable) {
            // La collecte ne doit jamais perturber la détection.
        }
        val savedCount = col?.savedCount ?: 0
        val colError = col?.lastError ?: ""

        publish {
            it.copy(
                closedScore = r.closedScore,
                openScore = r.openScore,
                processingMs = r.processingMs,
                framesProcessed = frames,
                sessionCount = machine.count,
                machineState = machine.state.name,
                hasTemplates = true,
                lastConfidence = machine.lastConfidence,
                errorCount = errors,
                message = if (counted) "Coffre compté" else "",
                savedCaptures = savedCount,
                collectorError = colError
            )
        }
    }

    // --------------------------------------------------------------- calibration

    private fun queueTemplate(intent: Intent) {
        if (projection == null) {
            publish { it.copy(message = "Capture non démarrée") }
            stopSelf()
            return
        }
        val kind = try {
            TemplateKind.valueOf(intent.getStringExtra(EXTRA_KIND) ?: "")
        } catch (t: Throwable) {
            TemplateKind.CLOSED
        }
        pending = PendingTemplate(
            kind,
            intent.getFloatExtra(EXTRA_LEFT, 0f),
            intent.getFloatExtra(EXTRA_TOP, 0f),
            intent.getFloatExtra(EXTRA_RIGHT, 1f),
            intent.getFloatExtra(EXTRA_BOTTOM, 1f)
        )
        publish { it.copy(message = "Enregistrement du modèle…") }
    }

    private fun saveTemplate(d: ChestDetector, p: PendingTemplate, bmp: Bitmap) {
        val x0 = (p.left * bmp.width).toInt().coerceIn(0, bmp.width - 1)
        val y0 = (p.top * bmp.height).toInt().coerceIn(0, bmp.height - 1)
        val x1 = (p.right * bmp.width).toInt().coerceIn(x0 + 1, bmp.width)
        val y1 = (p.bottom * bmp.height).toInt().coerceIn(y0 + 1, bmp.height)
        if (x1 - x0 < 16 || y1 - y0 < 16) {
            publish { it.copy(message = "Zone trop petite pour un modèle") }
            return
        }
        val crop = Bitmap.createBitmap(bmp, x0, y0, x1 - x0, y1 - y0)
        val ok = d.saveTemplate(templatesDir(), p.kind, crop)
        crop.recycle()
        machine.reset()
        publish {
            it.copy(
                hasTemplates = d.hasTemplates(),
                message = if (ok) "Modèle ${p.kind.name} enregistré" else "Échec d'enregistrement du modèle"
            )
        }
    }

    private fun templatesDir() = File(filesDir, "templates")

    // -------------------------------------------------------------------- arrêt

    private fun stopCapture() {
        if (stopping) return
        stopping = true
        pending = null

        try { virtualDisplay?.release() } catch (t: Throwable) { }
        virtualDisplay = null

        try {
            imageReader?.setOnImageAvailableListener(null, null)
            imageReader?.close()
        } catch (t: Throwable) { }
        imageReader = null

        try { projection?.stop() } catch (t: Throwable) { }
        projection = null

        thread?.quitSafely()
        thread = null

        detector?.release()
        detector = null

        collector = null

        try { hud?.hide() } catch (t: Throwable) { }
        hud = null

        publish { CaptureStatus(running = false, message = "Capture arrêtée") }
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (t: Throwable) { }
        stopSelf()
    }

    // ------------------------------------------------------------------- outils

    private fun captureSize(): Triple<Int, Int, Int> {
        val wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val realW: Int
        val realH: Int
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            realW = b.width()
            realH = b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            realW = dm.widthPixels
            realH = dm.heightPixels
        }
        val scale = min(1f, MAX_CAPTURE_SIDE.toFloat() / max(realW, realH))
        val w = max(2, ((realW * scale).toInt() / 2) * 2)
        val h = max(2, ((realH * scale).toInt() / 2) * 2)
        return Triple(w, h, resources.displayMetrics.densityDpi)
    }

    @Suppress("DEPRECATION")
    private fun getResultData(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        } else {
            intent.getParcelableExtra<Intent>(EXTRA_RESULT_DATA)
        }
    }

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun buildNotification(): Notification {
        val stopPi = PendingIntent.getService(
            this, 1, stopIntent(this),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .addAction(0, "Arrêter", stopPi)
            .build()
    }

    private fun publish(block: (CaptureStatus) -> CaptureStatus) {
        _status.update(block)
    }

    companion object {
        const val ACTION_START = "com.chestcounter.action.START"
        const val ACTION_STOP = "com.chestcounter.action.STOP"
        const val ACTION_SAVE_TEMPLATE = "com.chestcounter.action.SAVE_TEMPLATE"

        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_KIND = "kind"
        const val EXTRA_LEFT = "left"
        const val EXTRA_TOP = "top"
        const val EXTRA_RIGHT = "right"
        const val EXTRA_BOTTOM = "bottom"

        private const val CHANNEL_ID = "chestcounter_capture"
        private const val NOTIF_ID = 1001
        private const val INTERVAL_MS = 200L
        private const val MAX_CAPTURE_SIDE = 1280

        private val _status = MutableStateFlow(CaptureStatus())
        val status: StateFlow<CaptureStatus> = _status.asStateFlow()

        fun startIntent(context: Context, resultCode: Int, data: Intent): Intent =
            Intent(context, CaptureService::class.java)
                .setAction(ACTION_START)
                .putExtra(EXTRA_RESULT_CODE, resultCode)
                .putExtra(EXTRA_RESULT_DATA, data)

        fun stopIntent(context: Context): Intent =
            Intent(context, CaptureService::class.java).setAction(ACTION_STOP)

        fun templateIntent(
            context: Context,
            kind: TemplateKind,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float
        ): Intent =
            Intent(context, CaptureService::class.java)
                .setAction(ACTION_SAVE_TEMPLATE)
                .putExtra(EXTRA_KIND, kind.name)
                .putExtra(EXTRA_LEFT, left)
                .putExtra(EXTRA_TOP, top)
                .putExtra(EXTRA_RIGHT, right)
                .putExtra(EXTRA_BOTTOM, bottom)
    }
}
