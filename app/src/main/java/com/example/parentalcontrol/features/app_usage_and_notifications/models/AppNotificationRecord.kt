package com.example.parentalcontrol.features.app_usage_and_notifications.models

import kotlinx.serialization.Serializable

/**
 * AppNotificationRecord — نموذج بيانات الإشعار الوارد والملتقط عبر NotificationListener
 */
@Serializable
data class AppNotificationRecord(
    val device_id: String,
    val package_name: String,
    val app_name: String,
    val title: String,
    val content: String,
    val post_time: String
)
