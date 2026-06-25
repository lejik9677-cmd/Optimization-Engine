package com.example.parentalcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.BatteryManager
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import kotlinx.datetime.Clock
import java.util.UUID

/**
 * مدير تتبع الموقع الشفاف والأخلاقي
 *
 * ⚠️ المبادئ الأخلاقية المطبقة:
 * 1. ✅ إشعار دائم في شريط الإشعارات عند التتبع النشط
 * 2. ✅ طلب موافقة صريحة من المستخدم
 * 3. ✅ السماح بإيقاف التتبع في أي وقت
 * 4. ✅ شفافية كاملة حول البيانات المجمعة
 * 5. ✅ احترام إعدادات الموقع في النظام
 *
 * الاستخدام:
 * ```
 * val tracker = LocationTrackerManager(context)
 *
 * // التحقق من الموافقة
 * if (tracker.hasUserConsent()) {
 *     tracker.startTracking()
 * }
 * ```
 */
class LocationTrackerManager(private val context: Context) {

    companion object {
        private const val TAG = "LocationTracker"
        private const val PREFS_NAME = "location_tracking_prefs"
        private const val KEY_USER_CONSENT = "user_consent_given"
        private const val KEY_CONSENT_TIMESTAMP = "consent_timestamp"
        private const val KEY_TRACKING_ENABLED = "tracking_enabled"
        private const val NOTIFICATION_CHANNEL_ID = "location_tracking_channel"
        private const val NOTIFICATION_ID = 1001

        // التحديث كل 10 دقائق (كما طلبت)
        const val UPDATE_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes
        const val FASTEST_UPDATE_INTERVAL_MS = 5 * 60 * 1000L // 5 minutes
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val supabaseManager = SupabaseManager.getInstance()

    private var locationCallback: LocationCallback? = null

    /**
     * التحقق من موافقة المستخدم
     */
    fun hasUserConsent(): Boolean {
        return prefs.getBoolean(KEY_USER_CONSENT, false)
    }

    /**
     * تسجيل موافقة المستخدم
     * ⚠️ يجب استدعاؤها فقط بعد موافقة صريحة من المستخدم
     */
    fun setUserConsent(granted: Boolean) {
        prefs.edit().apply {
            putBoolean(KEY_USER_CONSENT, granted)
            if (granted) {
                putLong(KEY_CONSENT_TIMESTAMP, System.currentTimeMillis())
            } else {
                remove(KEY_CONSENT_TIMESTAMP)
                setTrackingEnabled(false)
            }
            apply()
        }

        Log.i(TAG, "User consent: $granted")
    }

    /**
     * التحقق من حالة التتبع
     */
    fun isTrackingEnabled(): Boolean {
        return prefs.getBoolean(KEY_TRACKING_ENABLED, false)
    }

    /**
     * تفعيل/تعطيل التتبع
     */
    fun setTrackingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_TRACKING_ENABLED, enabled).apply()
    }

    /**
     * التحقق من الصلاحيات المطلوبة
     */
    fun hasLocationPermissions(): Boolean {
        val fineLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseLocation = ActivityCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineLocation && coarseLocation
    }

    /**
     * التحقق من صلاحية الموقع في الخلفية (Android 10+)
     */
    fun hasBackgroundLocationPermission(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
        return true // Not required for older versions
    }

    /**
     * بدء تتبع الموقع
     * ⚠️ يعرض إشعار دائم للشفافية
     */
    @SuppressLint("MissingPermission")
    suspend fun startTracking(): Boolean = withContext(Dispatchers.Main) {
        try {
            // التحقق من الموافقة
            if (!hasUserConsent()) {
                Log.e(TAG, "Cannot start tracking: User consent not given")
                return@withContext false
            }

            // التحقق من الصلاحيات
            if (!hasLocationPermissions()) {
                Log.e(TAG, "Cannot start tracking: Permissions not granted")
                return@withContext false
            }

            // إنشاء قناة الإشعارات
            createNotificationChannel()

            // عرض إشعار دائم (للشفافية)
            showTrackingNotification()

            // إعداد طلب الموقع بأعلى دقة ممكنة (GPS الحقيقي)
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                UPDATE_INTERVAL_MS
            ).apply {
                setMinUpdateIntervalMillis(FASTEST_UPDATE_INTERVAL_MS)
                setWaitForAccurateLocation(true)   // ✅ ننتظر حتى يثبت الـ GPS
                setMinUpdateDistanceMeters(10f)     // ✅ لا نحفظ إذا تحرك أقل من 10 أمتار
            }.build()

            // Callback لتلقي التحديثات
            locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    result.lastLocation?.let { location ->
                        handleLocationUpdate(location)
                    }
                }
            }

            // بدء تلقي التحديثات
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                context.mainLooper
            )

            setTrackingEnabled(true)
            Log.i(TAG, "Location tracking started")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start tracking: ${e.message}", e)
            false
        }
    }

    /**
     * إيقاف تتبع الموقع
     */
    suspend fun stopTracking(): Boolean = withContext(Dispatchers.Main) {
        try {
            locationCallback?.let {
                fusedLocationClient.removeLocationUpdates(it)
            }

            // إخفاء الإشعار
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)

            setTrackingEnabled(false)
            Log.i(TAG, "Location tracking stopped")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop tracking: ${e.message}", e)
            false
        }
    }

    /**
     * معالجة تحديث الموقع
     * ✅ نفلتر النقاط الضعيفة (أسوأ من 50 متر) لضمان دقة الخريطة
     */
    private fun handleLocationUpdate(location: Location) {
        val accuracyM = location.accuracy
        Log.i(TAG, "Location update: ${location.latitude}, ${location.longitude} (accuracy: ${accuracyM}m)")

        // ✅ فلتر الدقة: نتجاهل النقاط الضعيفة جداً
        val MAX_ACCEPTABLE_ACCURACY_M = 50f
        if (accuracyM > MAX_ACCEPTABLE_ACCURACY_M) {
            Log.w(TAG, "Skipping weak GPS point: accuracy=${accuracyM}m > ${MAX_ACCEPTABLE_ACCURACY_M}m")
            return
        }

        // حفظ الموقع في Supabase
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            saveLocationToSupabase(location)
        }
    }

    /**
     * حفظ الموقع في Supabase
     */
    private suspend fun saveLocationToSupabase(location: Location) {
        try {
            val deviceId = getDeviceId()
            val batteryInfo = getBatteryInfo()

            val locationData = LocationData(
                latitude = location.latitude,
                longitude = location.longitude,
                accuracy = location.accuracy,
                timestamp = Clock.System.now().toString(),
                deviceId = deviceId,
                batteryLevel = batteryInfo.first,
                isCharging = batteryInfo.second
            )

            // استخدام SupabaseManager لحفظ البيانات
            val result = supabaseManager.saveLocation(locationData)

            when (result) {
                is LocationSaveResult.Success -> {
                    Log.i(TAG, "Location saved to Supabase: ${result.locationId}")
                }
                is LocationSaveResult.Error -> {
                    Log.e(TAG, "Failed to save location: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving location: ${e.message}", e)
        }
    }

    /**
     * الحصول على الموقع الحالي (لمرة واحدة)
     */
    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): LocationResult = withContext(Dispatchers.IO) {
        try {
            if (!hasLocationPermissions()) {
                return@withContext LocationResult.Error("Location permissions not granted")
            }

            val location = fusedLocationClient.lastLocation.await()

            if (location != null) {
                LocationResult.Success(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = location.time
                )
            } else {
                LocationResult.Error("Location not available")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location: ${e.message}", e)
            LocationResult.Error("Failed to get location: ${e.message}")
        }
    }

    /**
     * الحصول على معرف الجهاز الفريد
     */
    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ) ?: UUID.randomUUID().toString()
    }

    /**
     * الحصول على معلومات البطارية
     */
    private fun getBatteryInfo(): Pair<Int?, Boolean?> {
        return try {
            val batteryManager = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
            val level = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
            val isCharging = batteryManager.isCharging

            Pair(level, isCharging)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting battery info: ${e.message}")
            Pair(null, null)
        }
    }

    /**
     * إنشاء قناة الإشعارات
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Location Tracking"
            val descriptionText = "Shows when location tracking is active"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(NOTIFICATION_CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    /**
     * عرض إشعار التتبع النشط (للشفافية)
     * ⚠️ هذا إشعار دائم يظهر طالما التتبع نشط
     */
    private fun showTrackingNotification() {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("🌍 Location Tracking Active")
            .setContentText("Your location is being shared for safety. Tap to manage.")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true) // لا يمكن رفضه (للشفافية)
            .setAutoCancel(false)
            .build()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    /**
     * حذف جميع بيانات الموقع من Supabase
     * ⚠️ حق المستخدم في حذف بياناته
     */
    suspend fun deleteAllLocationData(): Boolean {
        return try {
            val deviceId = getDeviceId()
            supabaseManager.deleteLocationsByDeviceId(deviceId)
            Log.i(TAG, "All location data deleted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete location data: ${e.message}", e)
            false
        }
    }
}
