package com.chestcounter.capture

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
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
import com.chestcounter.R
import com.chestcounter.data.AppDb
import com.chestcounter.data.ChestEvent
import com.chestcounter.detect.ChestDetector
import com.chestcounter.detect.ChestStateMachine
import com.chestcounter.detect.Observation
import com.chestcounter.ui.Hud
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

class CaptureService : Service() {

    companion object {
        const val ACTION_START = "com.chestcounter.START"
        const val ACTION_STOP = "com.chestcounter.STOP"
        const val ACTION_CALIB_CLOSED = "com.chestcounter.CALIB_CLOSED"
        const val ACTION_CALIB_OPEN = "com.chestcounter.CALIB_OPEN"
        const val ACTION_CLEAR_CALIB = "com.chestcounter.CLEAR_CALIB"
        const val ACTION_UNDO = "com.chestcounter.UNDO"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"

        private const val CHANNEL_ID = "capture"
        private const val NOTIF_ID = 1
        private const val MAX_SIDE = 1280
        private const val MIN_INTERVAL_MS = 200L

        val running = MutableStateFlow(false)
        val status = MutableStateFlow("")
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var workThread: HandlerThread? = null
    private var workHandler: Handler? = null

    private var projection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null
    private var display: VirtualDisplay? = null
    private var reader: ImageReader? = null
    private var detector: ChestDetector? = null
    private val machine = ChestStateMachine()
    private var hud: Hud? = null

    @Volatile private var lastFrame: Bitmap? = null
    private var lastRun = 0L
    @Volatile private var total = 0
    @Volatile private var obs = Observation.NONE
    private var stopped = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (val action = intent?.action) {
            ACTION_START -> start(intent)
            null -> {}
            else -> handleAction(action)
        }
        return START_NOT_STICKY
    }

    private fun start(intent: Intent) {
        if (projection != null) return
        createChannel()
        val notif = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(getString(R.string.notif_text))
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .setOngoing(true)
            .build()
        // Le service doit passer au premier plan AVANT de récupérer la projection.
        startForeground(NOTIF_ID, notif, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)

        val code = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_DATA)
        }
        if (data == null) {
            fail("Autorisation de capture manquante")
            return
        }

        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val mp = try {
            mpm.getMediaProjection(code, data)
        } catch (e: Exception) {
            null
        }
        if (mp == null) {
            fail("Capture refusée par Android")
            return
        }
        projection = mp
        val cb = object : MediaProjection.Callback() {
            override fun onStop() {
                mainHandler.post { stopAll() }
            }
        }
        projectionCallback = cb
        mp.registerCallback(cb, mainHandler)

        val thread = HandlerThread("chest-work").also { it.start() }
        workThread = thread
        workHandler = Handler(thread.looper)

        val det = ChestDetector(this)
        if (!det.init()) {
            fail("OpenCV n'a pas démarré")
            return
        }
        detector = det

        val m = resources.displayMetrics
        val scale = min(1f, MAX_SIDE.toFloat() / max(m.widthPixels, m.heightPixels))
        val w = (m.widthPixels * scale).toInt().coerceAtLeast(2)
        val h = (m.heightPixels * scale).toInt().coerceAtLeast(2)

        val r = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)
        reader = r
        r.setOnImageAvailableListener({ onImage(it) }, workHandler)
        display = mp.createVirtualDisplay(
            "chest-capture", w, h, m.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            r.surface, null, workHandler
        )

        hud = Hud(this) { action -> handleAction(action) }.also { it.show() }
        ioScope.launch {
            AppDb.get(this@CaptureService).dao().totalFlow().collect {
                total = it
                refreshHud()
            }
        }

        running.value = true
        status.value = if (det.ready) "Capture active" else "Capture active : calibration à faire"
        refreshHud()
    }

    private fun onImage(r: ImageReader) {
        val image = try {
            r.acquireLatestImage()
        } catch (e: Exception) {
            null
        } ?: return
        try {
            val now = SystemClock.elapsedRealtime()
            if (now - lastRun < MIN_INTERVAL_MS) return
            lastRun = now
            val bmp = toBitmap(image)
            lastFrame = bmp
            val det = detector ?: return
            val o = det.analyze(bmp)
            obs = o
            if (machine.update(o, now)) {
                ioScope.launch {
                    AppDb.get(this@CaptureService).dao()
                        .insert(ChestEvent(timestamp = System.currentTimeMillis()))
                }
            }
            refreshHud()
        } catch (e: Exception) {
            status.value = "Erreur d'analyse : ${e.message}"
        } finally {
            image.close()
        }
    }

    private fun toBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val rowPadding = plane.rowStride - plane.pixelStride * image.width
        val full = Bitmap.createBitmap(
            image.width + rowPadding / plane.pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        full.copyPixelsFromBuffer(plane.buffer)
        return if (rowPadding == 0) full
        else Bitmap.createBitmap(full, 0, 0, image.width, image.height)
    }

    private fun handleAction(action: String) {
        when (action) {
            ACTION_STOP -> mainHandler.post { stopAll() }
            ACTION_CALIB_CLOSED -> calibrate(isOpen = false)
            ACTION_CALIB_OPEN -> calibrate(isOpen = true)
            ACTION_CLEAR_CALIB -> workHandler?.post {
                detector?.clearTemplates()
                machine.reset()
                status.value = "Calibration effacée"
                refreshHud()
            }
            ACTION_UNDO -> ioScope.launch {
                AppDb.get(this@CaptureService).dao().deleteLast()
            }
        }
    }

    /** Enregistre la zone centrale de l'écran (25 % x 25 %) comme modèle. */
    private fun calibrate(isOpen: Boolean) {
        workHandler?.post {
            val f = lastFrame
            val det = detector
            if (f == null || det == null) {
                status.value = "Pas encore d'image capturée"
                refreshHud()
                return@post
            }
            val w = (f.width * 0.25f).toInt()
            val h = (f.height * 0.25f).toInt()
            val x = (f.width - w) / 2
            val y = (f.height - h) / 2
            val ok = det.saveTemplate(f, x, y, w, h, isOpen)
            machine.reset()
            status.value = when {
                !ok -> "Échec de l'enregistrement"
                isOpen -> "Modèle OUVERT enregistré"
                else -> "Modèle FERMÉ enregistré"
            }
            refreshHud()
        }
    }

    private fun refreshHud() {
        val t = total
        val o = obs
        val ready = detector?.ready == true
        val msg = status.value
        mainHandler.post { hud?.update(t, o, ready, msg) }
    }

    private fun fail(message: String) {
        status.value = message
        stopAll()
    }

    private fun stopAll() {
        if (stopped) return
        stopped = true
        running.value = false
        hud?.hide()
        hud = null
        display?.release()
        display = null
        reader?.close()
        reader = null
        projectionCallback?.let { projection?.unregisterCallback(it) }
        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null
        workThread?.quitSafely()
        workThread = null
        ioScope.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopAll()
        super.onDestroy()
    }

    private fun createChannel() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notif_channel),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
}
