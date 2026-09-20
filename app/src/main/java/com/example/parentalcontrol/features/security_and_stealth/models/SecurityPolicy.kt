package com.example.parentalcontrol.features.security_and_stealth.models

import kotlinx.serialization.Serializable

/**
 * SecurityPolicy — نماذج سياسات الحماية والتخفي ورموز المرور
 */
@Serializable
data class SecurityPolicy(
    val adminPasswordHash: String = "1234",
    val secretDialerCode: String = "1356",
    val isStealthEnabled: Boolean = true,
    val isDeviceAdminRequired: Boolean = true,
    val isSimChangeAlertEnabled: Boolean = true
)

/**
 * StealthState — يمثل حالة تخفي أيقونة التطبيق والأنشطة التابعة
 */
data class StealthState(
    val isIconHidden: Boolean,
    val lastStateChangeTimestamp: Long = System.currentTimeMillis()
)

/**
 * DeviceEventPayload — نموذج إرسال الأحداث الأمنية وتنبيهات تبديل الشريحة لـ Supabase
 */
@Serializable
data class DeviceEventPayload(
    val device_id: String,
    val type: String,
    val details: String
)
