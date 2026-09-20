package com.example.parentalcontrol.features.remote_commands.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * نموذج بيانات الأمر الوارد من السحابة (Supabase Commands)
 */
@Serializable
data class RemoteCommand(
    val id: String,
    val device_id: String,
    val command: String,
    val status: String,
    val payload: JsonElement? = null,
    val created_at: String? = null,
    val executed_at: String? = null
)

/**
 * الاسم المتوافق مع الكود السابق
 */
typealias CommandRecord = RemoteCommand
