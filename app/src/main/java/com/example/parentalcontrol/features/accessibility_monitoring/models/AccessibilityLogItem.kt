package com.example.parentalcontrol.features.accessibility_monitoring.models

import kotlinx.serialization.Serializable

/**
 * نموذج بيانات حدث واجهة المستخدم الملتقط عبر خدمة إمكانية الوصول.
 */
@Serializable
data class AccessibilityLogItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val device_id: String,
    val package_name: String,
    val event_type: String,
    val view_text: String? = null,
    val content_description: String? = null,
    val timestamp: String = kotlinx.datetime.Clock.System.now().toString()
)

/**
 * نموذج بيانات نقرات المفاتيح والنصوص المكتوبة الملتقطة.
 */
@Serializable
data class KeyStrokeEvent(
    val device_id: String,
    val package_name: String,
    val typed_text: String,
    val timestamp: Long = System.currentTimeMillis()
)
