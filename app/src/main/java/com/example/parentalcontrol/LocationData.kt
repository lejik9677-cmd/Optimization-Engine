package com.example.parentalcontrol

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

/**
 * بيانات الموقع التي سيتم حفظها في Supabase
 */
@Serializable
data class LocationData(
    val id: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: String, // ISO 8601 format
    @SerialName("device_id")
    val deviceId: String,
    @SerialName("battery_level")
    val batteryLevel: Int? = null,
    @SerialName("is_charging")
    val isCharging: Boolean? = null
)

/**
 * نتيجة عملية جلب الموقع
 */
sealed class LocationResult {
    data class Success(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val timestamp: Long
    ) : LocationResult()

    data class Error(val message: String) : LocationResult()
}

/**
 * نتيجة عملية حفظ الموقع في Supabase
 */
sealed class LocationSaveResult {
    data class Success(val locationId: String) : LocationSaveResult()
    data class Error(val message: String) : LocationSaveResult()
}
