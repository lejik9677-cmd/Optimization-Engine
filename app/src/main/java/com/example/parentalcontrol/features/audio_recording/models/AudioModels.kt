package com.example.parentalcontrol.features.audio_recording.models

import kotlinx.serialization.Serializable

/**
 * حالات الميكروفون في المنظومة
 */
enum class MicState {
    IDLE,
    RECORDING,
    STREAMING,
    CONFLICT_BUSY
}

/**
 * نموذج قطعة البث الحي للصوت (Audio Chunk)
 */
@Serializable
data class AudioChunk(
    val chunk: String,
    val sampleRate: Int = 16_000,
    val timestamp: Long
)

/**
 * إعدادات ترميز وضغط الصوت المعتمدة
 * (16kHz / 32kbps Mono) ومهلة الأمان للبث المباشر (5 دقائق)
 */
data class AudioEncodingConfig(
    val sampleRate: Int = 16_000,
    val bitRate: Int = 32_000,
    val channels: Int = 1,
    val maxStreamDurationMs: Long = 5 * 60 * 1000L
)
