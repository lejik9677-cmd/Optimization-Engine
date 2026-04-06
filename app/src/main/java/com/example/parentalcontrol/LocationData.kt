package com.example.parentalcontrol

import kotlinx.serialization.Serializable

/**
 * بيانات الموقع التي سيتم حفظها في Supabase
 *
 * ⚠️ هام: هذه البيانات حساسة ويجب:
 * 1. الحصول على موافقة صريحة من المستخدم
 * 2. إظهار إشعار واضح عند التتبع
 * 3. السماح بإيقاف التتبع في أي وقت
 * 4. حذف البيانات عند الطلب
 */
@Serializable
data class LocationData(
    val id: String? = null,
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val timestamp: String, // ISO 8601 format
    val deviceId: String,
    val batteryLevel: Int? = null,
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
