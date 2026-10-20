package com.aperturelab.gesturecontrol.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraState
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.aperturelab.gesturecontrol.MainActivity
import com.aperturelab.gesturecontrol.R
import com.aperturelab.gesturecontrol.domain.FrameConverter
import com.aperturelab.gesturecontrol.domain.GestureEngine
import com.aperturelab.gesturecontrol.domain.GestureEvent
import com.aperturelab.gesturecontrol.domain.HandFrame
import com.aperturelab.gesturecontrol.domain.RawFrame
import com.aperturelab.gesturecontrol.inference.MediaPipeGestureRecognizer
import com.aperturelab.gesturecontrol.overlay.StatusOverlay
import com.aperturelab.gesturecontrol.runtime.ActivationState
import com.aperturelab.gesturecontrol.runtime.GestureRuntime
import com.aperturelab.gesturecontrol.settings.SettingsRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Foreground service that owns the camera. It is armed once the feature is
 * configured and stays alive, but only mounts the camera while
 * [GestureRuntime.cameraRequested] is true — which the accessibility layer
 * sets when an enabled application is in the foreground.
 *
 * The notification is posted before anything else happens so the service can
 * never miss the platform's startForeground deadline.
 */
class CameraForegroundService : LifecycleService(), MediaPipeGestureRecognizer.Listener {

    private val analysisExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "gesture-analysis")
    }

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var recognizer: MediaPipeGestureRecognizer? = null
    private val engine = GestureEngine()

    private var mounted = false
    private var screenOff = false
    private var intentionalUnmount = false

    private var retryJob: Job? = null
    private var retryAttempt = 0

    // Simple diagnostics counters, logged periodically while mounted.
    @Volatile private var framesSeen = 0L
    @Volatile private var framesSkipped = 0L
    @Volatile private var framesDropped = 0L
    @Volatile private var framesAtLastStats = 0L
    @Volatile private var lastStatsAtMs = 0L
    @Volatile private var lastFrameAtMs = 0L
    @Volatile private var resultsSeen = 0L
    @Volatile private var framesWithHand = 0L
    @Volatile private var lastResultAtMs = 0L

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    screenOff = true
                    Log.i(TAG, "screen off: unmounting camera")
                    if (mounted) unmountCamera()
                    GestureRuntime.activation.value = ActivationState.INACTIVE_NOT_ENABLED
                }
                Intent.ACTION_SCREEN_ON -> {
                    screenOff = false
                    Log.i(TAG, "screen on: requested=${GestureRuntime.cameraRequested.value}")
                    if (GestureRuntime.cameraRequested.value) mountCamera()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        GestureRuntime.armed.value = true
        createNotificationChannel()
        ContextCompat.registerReceiver(
            this,
            screenReceiver,
            IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        lifecycleScope.launch {
            GestureRuntime.cameraRequested.collect { requested ->
                Log.i(TAG, "cameraRequested=$requested")
                if (requested) mountCamera() else unmountCamera()
            }
        }
        lifecycleScope.launch {
            SettingsRepository.get(this@CameraForegroundService).config.collect { config ->
                Log.i(TAG, "config applied in-engine: threshold=${config.swipeThresholdRatio} consistency=${config.consistencyRatio} cooldown=${config.swipeCooldownMs}")
                engine.updateConfig(config)
            }
        }
        lifecycleScope.launch {
            kotlinx.coroutines.flow.combine(
                GestureRuntime.activation,
                GestureRuntime.paused,
            ) { activation, paused -> activation to paused }
                .collect { (activation, paused) -> StatusOverlay.setState(activation, paused) }
        }
        StatusOverlay.attach(this)
        startResultWatchdog()
        obtainProvider()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        // Must happen before any camera work. If the platform rejects the
        // camera foreground type (started from the background), fail quietly
        // instead of crashing the shared process that also hosts the
        // accessibility service.
        try {
            startForeground(NOTIFICATION_ID, buildNotification())
        } catch (error: RuntimeException) {
            Log.w(TAG, "startForeground rejected: ${error.message}")
            stopSelf()
            return START_NOT_STICKY
        }
        when (intent?.action) {
            ACTION_MOUNT -> GestureRuntime.cameraRequested.value = true
            ACTION_UNMOUNT -> GestureRuntime.cameraRequested.value = false
        }
        // Re-arms the badge when it was skipped because the overlay permission
        // had not been granted yet.
        if (!StatusOverlay.isShowing) StatusOverlay.attach(this)
        return START_STICKY
    }

    override fun onDestroy() {
        GestureRuntime.armed.value = false
        StatusOverlay.detach()
        retryJob?.cancel()
        unregisterReceiver(screenReceiver)
        unmountCamera()
        recognizer?.close()
        recognizer = null
        analysisExecutor.shutdown()
        super.onDestroy()
    }

    // ------------------------------------------------------------------ camera

    private fun obtainProvider() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener(
            {
                cameraProvider = runCatching { future.get() }
                    .onFailure { Log.w(TAG, "camera provider unavailable", it) }
                    .getOrNull()
                Log.i(TAG, "camera provider ready=${cameraProvider != null} requested=${GestureRuntime.cameraRequested.value} screenOff=$screenOff")
                if (GestureRuntime.cameraRequested.value && !screenOff) mountCamera()
            },
            ContextCompat.getMainExecutor(this),
        )
    }

    private fun mountCamera() {
        if (mounted) {
            Log.d(TAG, "mount skipped: already mounted")
            return
        }
        if (screenOff) {
            Log.d(TAG, "mount skipped: screen off")
            return
        }
        val provider = cameraProvider
        if (provider == null) {
            Log.w(TAG, "mount skipped: camera provider not ready")
            return
        }
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "mount skipped: camera permission missing")
            GestureRuntime.activation.value = ActivationState.UNAVAILABLE
            return
        }

        intentionalUnmount = false
        val analysis = ImageAnalysis.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setResolutionStrategy(
                        ResolutionStrategy(
                            Size(ANALYSIS_WIDTH, ANALYSIS_HEIGHT),
                            ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER,
                        ),
                    )
                    .build(),
            )
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        analysis.setAnalyzer(analysisExecutor) { proxy -> analyze(proxy) }

        try {
            camera = provider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_FRONT_CAMERA,
                analysis,
            )
        } catch (error: Exception) {
            Log.w(TAG, "Camera bind failed", error)
            scheduleRetry()
            return
        }

        imageAnalysis = analysis
        mounted = true
        retryAttempt = 0
        lastResultAtMs = android.os.SystemClock.elapsedRealtime()
        GestureRuntime.activation.value = ActivationState.WARMING
        Log.i(TAG, "camera mounted (warming)")
        observeCameraState()
    }

    private fun unmountCamera() {
        if (!mounted) {
            imageAnalysis?.clearAnalyzer()
            imageAnalysis = null
            return
        }
        intentionalUnmount = true
        imageAnalysis?.clearAnalyzer()
        imageAnalysis = null
        runCatching { cameraProvider?.unbindAll() }
        camera = null
        mounted = false
        engine.reset()
        lastFrameAtMs = 0L
        GestureRuntime.activation.value = ActivationState.INACTIVE_NOT_ENABLED
        Log.i(TAG, "camera unmounted")
    }

    private fun observeCameraState() {
        val current = camera ?: return
        current.cameraInfo.cameraState.observe(this) { state ->
            if (state.type == CameraState.Type.CLOSED && state.error != null) {
                if (!intentionalUnmount && GestureRuntime.cameraRequested.value && !screenOff) {
                    Log.w(TAG, "Camera session lost: ${state.error?.code}")
                    mounted = false
                    imageAnalysis?.clearAnalyzer()
                    imageAnalysis = null
                    scheduleRetry()
                }
            }
        }
    }

    private fun scheduleRetry() {
        if (retryJob?.isActive == true) return
        GestureRuntime.activation.value = ActivationState.UNAVAILABLE
        retryJob = lifecycleScope.launch {
            val backoff = RETRY_DELAYS_MS[retryAttempt.coerceAtMost(RETRY_DELAYS_MS.lastIndex)]
            retryAttempt++
            delay(backoff)
            if (GestureRuntime.cameraRequested.value && !screenOff) {
                unmountCamera()
                obtainProvider()
            }
        }
    }

    /**
     * A mounted session can stop producing results without a camera-state
     * error (for example while another application holds the camera). Report
     * that as unavailable and let the bounded retry path take over.
     */
    private fun startResultWatchdog() {
        lifecycleScope.launch {
            while (true) {
                delay(WATCHDOG_INTERVAL_MS)
                if (!mounted || screenOff || !GestureRuntime.cameraRequested.value) continue
                val now = android.os.SystemClock.elapsedRealtime()
                val last = lastResultAtMs
                if (last != 0L && now - last > RESULT_STALL_MS) {
                    Log.w(TAG, "no results for ${now - last} ms: camera may be preempted")
                    lastResultAtMs = now
                    scheduleRetry()
                }
            }
        }
    }

    // -------------------------------------------------------------- recognition

    private fun analyze(proxy: ImageProxy) {
        try {
            if (!mounted) return
            // Cap analysis at ~15 fps to keep inference and battery predictable.
            val now = android.os.SystemClock.elapsedRealtime()
            if (now - lastFrameAtMs < FRAME_INTERVAL_MS) {
                framesSkipped++
                return
            }
            lastFrameAtMs = now
            val activeRecognizer = ensureRecognizer()
            val plane = proxy.planes[0]
            val buffer = plane.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            val raw = RawFrame(
                width = proxy.width,
                height = proxy.height,
                rowStride = plane.rowStride,
                pixelStride = plane.pixelStride,
                rotationDegrees = proxy.imageInfo.rotationDegrees,
                plane = bytes,
            )
            val rgba = FrameConverter.convert(raw, mirrorHorizontally = true)
            val captureMs = proxy.imageInfo.timestamp / 1_000_000L
            framesSeen++
            val submitted = activeRecognizer.tryRecognize(rgba, captureMs)
            if (!submitted) framesDropped++
            logFrameStats()
        } catch (error: RuntimeException) {
            Log.w(TAG, "Frame analysis failed", error)
        } finally {
            proxy.close()
        }
    }

    private fun ensureRecognizer(): MediaPipeGestureRecognizer =
        recognizer ?: MediaPipeGestureRecognizer(this, this).also { recognizer = it }

    private fun logFrameStats() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (lastStatsAtMs == 0L) {
            lastStatsAtMs = now
            return
        }
        val elapsed = now - lastStatsAtMs
        if (elapsed < STATS_INTERVAL_MS) return
        val intervalFrames = framesSeen - framesAtLastStats
        val fps = intervalFrames * 1000.0 / elapsed
        Log.i(
            TAG,
            "analysis: %.1f fps, %d submitted, %d dropped, %d skipped, %d results, %d with hand"
                .format(fps, framesSeen, framesDropped, framesSkipped, resultsSeen, framesWithHand),
        )
        framesAtLastStats = framesSeen
        lastStatsAtMs = now
    }

    override fun onFrame(frame: HandFrame) {
        resultsSeen++
        lastResultAtMs = android.os.SystemClock.elapsedRealtime()
        if (frame.landmarks.isNotEmpty()) framesWithHand++
        if (GestureRuntime.activation.value == ActivationState.WARMING) {
            GestureRuntime.activation.value = ActivationState.ACTIVE
            Log.i(TAG, "activation: WARMING -> ACTIVE")
        }
        if (GestureRuntime.activation.value != ActivationState.ACTIVE) return
        val event = engine.onFrame(frame) ?: return
        Log.i(TAG, "gesture: $event (label=${frame.gestureLabel}, score=${frame.gestureScore})")
        GestureRuntime.gestureEvents.tryEmit(event)
    }

    override fun onError(message: String) {
        Log.w(TAG, message)
    }

    // ------------------------------------------------------------ notification

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_text))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    companion object {
        private const val TAG = "CameraService"
        private const val CHANNEL_ID = "gesture_camera"
        private const val NOTIFICATION_ID = 1
        private const val ANALYSIS_WIDTH = 640
        private const val ANALYSIS_HEIGHT = 480
        private const val STATS_INTERVAL_MS = 5_000L
        private const val FRAME_INTERVAL_MS = 66L
        private const val WATCHDOG_INTERVAL_MS = 2_000L
        private const val RESULT_STALL_MS = 3_000L
        private val RETRY_DELAYS_MS = longArrayOf(1_000, 2_000, 4_000, 8_000, 16_000, 30_000)

        const val ACTION_MOUNT = "com.aperturelab.gesturecontrol.action.MOUNT"
        const val ACTION_UNMOUNT = "com.aperturelab.gesturecontrol.action.UNMOUNT"

        /** Starts (or leaves running) the armed foreground service. */
        fun ensureStarted(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, CameraForegroundService::class.java),
            )
        }

        /** Mounts the camera for the current application (called by the activation layer). */
        fun requestMount() {
            GestureRuntime.cameraRequested.value = true
        }

        fun requestUnmount() {
            GestureRuntime.cameraRequested.value = false
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CameraForegroundService::class.java))
        }
    }
}
