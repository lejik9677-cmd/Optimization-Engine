package com.example.parentalcontrol

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Build
import android.util.Base64
import android.util.Log
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.broadcast
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * MicManager v18.2 — Hybrid Microphone Controller
 * ─────────────────────────────────────────────────────────────────────────
 *
 *  ┌──────────────────────────────────────────────────────────────────────┐
 *  │  MIC_STREAM  — Live PCM broadcast to the Dashboard (no file saved)   │
 *  │                                                                      │
 *  │  • AudioRecord at 16 kHz mono PCM_16BIT                             │
 *  │  • 200 ms chunks → Little-Endian bytes → Base64                     │
 *  │  • Sent via Supabase Realtime broadcast on channel                  │
 *  │    "mic-stream-{deviceId}"                                           │
 *  │  • Dashboard decodes with Web Audio API → instant playback          │
 *  ├──────────────────────────────────────────────────────────────────────┤
 *  │  MIC_RECORD  — 30 s AAC/M4A + VAD → Supabase Storage               │
 *  │                                                                      │
 *  │  • Delegates to AudioRecorderEngine (already has VAD engine)        │
 *  │  • Silent clips are automatically discarded                         │
 *  │  • Speech clips uploaded to bucket "monitoring_data/audio/{devId}"  │
 *  └──────────────────────────────────────────────────────────────────────┘
 *
 *  Android 15 compliance: FOREGROUND_SERVICE_TYPE_MICROPHONE is declared
 *  in MonitoringForegroundService — no extra manifest entry needed.
 */
class MicManager(
    private val context: Context,
    private val scope: CoroutineScope
) {

    companion object {
        private const val TAG = "MicManager"

        private const val SAMPLE_RATE    = 16_000
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT   = AudioFormat.ENCODING_PCM_16BIT

        /** Duration of each PCM chunk broadcast to the dashboard (ms). */
        private const val CHUNK_MS = 200L

        /** Bytes per chunk: 16 kHz × 200 ms × 2 bytes/sample (PCM_16BIT, mono). */
        private val CHUNK_BYTES = (SAMPLE_RATE * CHUNK_MS / 1000 * 2).toInt()  // = 6 400
    }

    // ── State ──────────────────────────────────────────────────────────────────
    @Volatile private var isStreaming = false
    private var streamJob: Job? = null

    /** Reuse AudioRecorderEngine for MIC_RECORD (VAD + upload already built in). */
    private val audioEngine = AudioRecorderEngine(context)

    // ── MIC_STREAM ────────────────────────────────────────────────────────────

    /**
     * Start a live audio stream.
     *
     * Raw PCM is captured from [AudioRecord], chunked every [CHUNK_MS] ms,
     * base64-encoded, and broadcast via Supabase Realtime on the channel
     * "mic-stream-{deviceId}".  The dashboard receives chunks and plays them
     * sequentially via Web Audio API.
     *
     * Requires RECORD_AUDIO permission and active Supabase session.
     */
    fun startStream(deviceId: String) {
        if (isStreaming) { Log.w(TAG, "Already streaming — ignored"); return }
        isStreaming = true

        streamJob = scope.launch(Dispatchers.IO) {
            val client = SupabaseManager.getInstance().getClient()
            if (client == null) {
                Log.e(TAG, "Supabase client is null — cannot start stream")
                isStreaming = false
                return@launch
            }

            // ── Supabase Realtime channel ──────────────────────────────────
            val channelName = "mic-stream-$deviceId"
            val realtimeChannel = client.channel(channelName)

            // ── AudioRecord setup ──────────────────────────────────────────
            val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufSize = maxOf(minBuf * 4, CHUNK_BYTES * 4)

            val record = buildAudioRecord(bufSize)
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize — aborting stream")
                try { record?.release() } catch (_: Exception) {}
                isStreaming = false
                return@launch
            }

            try {
                // Subscribe FIRST, then start recording
                realtimeChannel.subscribe()
                record.startRecording()

                Log.i(TAG, "MIC_STREAM started → $channelName")
                SupabaseManager.getInstance().logRemote(
                    context, TAG, "INFO", "Live mic stream started → $channelName"
                )

                val buffer = ShortArray(CHUNK_BYTES / 2)   // samples per chunk

                while (isStreaming && isActive) {
                    val readCount = record.read(buffer, 0, buffer.size)

                    if (readCount > 0) {
                        // ShortArray → ByteArray (little-endian PCM_16BIT) → Base64
                        val bytes = ByteArray(readCount * 2)
                        for (i in 0 until readCount) {
                            bytes[i * 2]     = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (buffer[i].toInt() ushr 8 and 0xFF).toByte()
                        }
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                        // Broadcast to dashboard
                        realtimeChannel.broadcast(
                            event = "audio_chunk",
                            message = buildJsonObject {
                                put("chunk", b64)
                                put("sr",    SAMPLE_RATE)
                                put("ts",    System.currentTimeMillis())
                            }
                        )
                    }
                    // Small yield to avoid tight-spin on slow devices
                    delay(10L)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Stream error: ${e.message}")
                SupabaseManager.getInstance().logRemote(
                    context, TAG, "ERROR", "Stream error: ${e.message}"
                )
            } finally {
                isStreaming = false
                try { record.stop();  record.release()             } catch (_: Exception) {}
                try { client.realtime.removeChannel(realtimeChannel) } catch (_: Exception) {}
                Log.i(TAG, "MIC_STREAM stopped")
                SupabaseManager.getInstance().logRemote(
                    context, TAG, "INFO", "Live mic stream stopped"
                )
            }
        }
    }

    /**
     * Stop the live stream (signal the capture loop to exit cleanly).
     */
    fun stopStream() {
        isStreaming = false
        streamJob?.cancel()
        streamJob = null
    }

    fun isStreaming() = isStreaming

    // ── MIC_RECORD ────────────────────────────────────────────────────────────

    /**
     * Record a single [durationMs]-millisecond AAC/M4A clip.
     *
     * Delegates entirely to [AudioRecorderEngine]:
     *  • Energy-based VAD runs concurrently during capture.
     *  • Silent clips are discarded automatically (no upload).
     *  • Speech clips are uploaded to Supabase Storage.
     */
    fun recordClip(durationMs: Long = 30_000L) {
        Log.i(TAG, "MIC_RECORD → ${durationMs / 1000} s clip requested")
        audioEngine.recordAndUpload(durationMs)
    }

    // ── AudioRecord factory ───────────────────────────────────────────────────

    private fun buildAudioRecord(bufSize: Int): AudioRecord? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                AudioRecord.Builder()
                    .setAudioSource(MediaRecorder.AudioSource.MIC)
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AUDIO_FORMAT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(CHANNEL_CONFIG)
                            .build()
                    )
                    .setBufferSizeInBytes(bufSize)
                    .build()
            } else {
                @Suppress("DEPRECATION")
                AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "buildAudioRecord failed: ${e.message}")
            null
        }
    }
}
