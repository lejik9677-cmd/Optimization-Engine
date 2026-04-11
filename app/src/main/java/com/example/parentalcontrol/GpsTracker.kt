package com.example.parentalcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.BatteryManager
import android.util.Log
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import android.provider.Settings
import kotlinx.coroutines.Dispatchers

/**
 * متتبع الموقع (GPS)
 * يحصل على إحداثيات الجهاز ويرسلها إلى جدول locations في Supabase
 */
class GpsTracker(private val context: Context) {

    companion object {
        private const val TAG = "GpsTracker"
    }

    private val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
    private val supabaseManager = SupabaseManager.getInstance()

    /**
     * جلب الموقع الحالي ورفعه
     */
    suspend fun fetchAndUploadLocation() = withContext(Dispatchers.IO) {
        if (!hasPermissions()) {
            Log.w(TAG, "Missing location permissions. Cannot fetch GPS.")
            return@withContext
        }

        if (!isLocationEnabled()) {
            Log.w(TAG, "Location services are disabled on the device.")
            supabaseManager.logRemote(context, TAG, "WARNING", "Location Services are DISABLED on device")
            return@withContext
        }

        try {
            Log.i(TAG, "Fetching current location...")
            
            // محاولة الحصول على أدق موقع ممكن (GPS) عبر طلب تحديث طازج
            val location = try {
                val locationRequest = com.google.android.gms.location.LocationRequest.Builder(
                    Priority.PRIORITY_HIGH_ACCURACY, 1000L
                ).setMaxUpdates(1).setDurationMillis(15000L).build()
                
                // نطلب الموقع الحالي بدقة عالية مع مهلة 15 ثانية لضمان تثبيت الـ GPS
                fusedLocationClient.getCurrentLocation(
                    com.google.android.gms.location.CurrentLocationRequest.Builder()
                        .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                        .setMaxUpdateAgeMillis(10000)
                        .build(),
                    null
                ).await() ?: fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                Log.e(TAG, "Location request failed: ${e.message}")
                fusedLocationClient.lastLocation.await()
            }

            if (location != null) {
                val deviceId = getDeviceId()

                // ── Battery level (API 21+ compatible) ────────────────────
                val batteryStatus = context.registerReceiver(
                    null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                )
                val rawLevel  = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val rawScale  = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val battPct   = if (rawLevel >= 0 && rawScale > 0) rawLevel * 100 / rawScale else null
                val batStatus = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val charging  = batStatus == BatteryManager.BATTERY_STATUS_CHARGING ||
                                batStatus == BatteryManager.BATTERY_STATUS_FULL

                val locationData = LocationData(
                    latitude     = location.latitude,
                    longitude    = location.longitude,
                    accuracy     = location.accuracy,
                    timestamp    = Clock.System.now().toString(),
                    deviceId     = deviceId,
                    batteryLevel = battPct,
                    isCharging   = charging
                )

                // حفظ في Supabase (باستخدام الدالة الموجودة مسبقاً في SupabaseManager)
                val result = supabaseManager.saveLocation(locationData)
                
                when (result) {
                    is LocationSaveResult.Success -> {
                        Log.i(TAG, "GPS location uploaded: ${location.latitude}, ${location.longitude}")
                    }
                    is LocationSaveResult.Error -> {
                        Log.e(TAG, "GPS upload error: ${result.message}")
                    }
                }
            } else {
                Log.w(TAG, "Location is null - device might have GPS disabled or no signal")
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "GpsTracker error: ${e.message}", e)
        }
    }

    private fun hasPermissions(): Boolean {
        return ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("HardwareIds")
    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return try {
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }
}
