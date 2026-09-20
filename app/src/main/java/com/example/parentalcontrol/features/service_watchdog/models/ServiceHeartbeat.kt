package com.example.parentalcontrol.features.service_watchdog.models

import kotlinx.serialization.Serializable

/**
 * نموذج نبض وصحة الخدمة لمراقبة استمرارية العمل
 */
@Serializable
data class ServiceHeartbeat(
    val device_id: String,
    val is_service_running: Boolean,
    val last_heartbeat: String = kotlinx.datetime.Clock.System.now().toString(),
    val battery_level: Int? = null,
    val is_charging: Boolean? = null,
    val active_modules: List<String> = emptyList(),
    val foreground_type: String? = null
)

/**
 * معلومات تشغيل وصحة الخدمة
 */
@Serializable
data class ServiceHealthInfo(
    val service_class: String,
    val uptime_ms: Long,
    val is_healthy: Boolean,
    val restart_count: Int = 0
)
