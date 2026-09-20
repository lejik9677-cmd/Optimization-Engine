package com.example.parentalcontrol.features.app_usage_and_notifications.models

import kotlinx.serialization.Serializable

/**
 * UsageStatRecord / AppUsageRecord — نموذج بيانات سجل استخدام التطبيقات الفردية
 */
@Serializable
data class UsageStatRecord(
    val device_id: String,
    val package_name: String,
    val app_name: String,
    val total_time_ms: Long,
    val duration_minutes: Long,
    val captured_at: String,
    val date: String
)

typealias AppUsageRecord = UsageStatRecord

/**
 * DailyUsagePayload — نموذج الملخص التراكمي اليومي لاستخدام التطبيقات
 */
@Serializable
data class DailyUsagePayload(
    val device_id: String,
    val total_active_time_ms: Long,
    val usage_summary: Map<String, Long>,
    val report_date: String
)
