package com.example.parentalcontrol

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
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

        try {
            Log.i(TAG, "Fetching current location...")
            
            // محاولة الحصول على آخر موقع معروف أو طلب الموقع الحالي
            val location = try {
                fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_BALANCED_POWER_ACCURACY,
                    null
                ).await() ?: fusedLocationClient.lastLocation.await()
            } catch (e: Exception) {
                Log.e(TAG, "Location request failed: ${e.message}")
                null
            }

            if (location != null) {
                val deviceId = getDeviceId()
                val locationData = LocationData(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = Clock.System.now().toString(),
                    deviceId = deviceId
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
        return Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }
}
