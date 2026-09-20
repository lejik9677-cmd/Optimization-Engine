package com.example.parentalcontrol.features.call_monitoring.models

import kotlinx.serialization.Serializable

/**
 * سجل المكالمة الهاتفية المتزامن مع Supabase
 */
@Serializable
data class CallLogRecord(
    val device_id: String,
    val call_type: String,
    val contact_name: String?,
    val phone_number: String?,
    val duration_seconds: Int,
    val timestamp: String
)

/**
 * حالات المكالمة الهاتفية
 */
enum class CallState {
    IDLE,
    RINGING,
    OFFHOOK
}
