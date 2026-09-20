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

        /** Maximum live stream duration before auto-stopping to prevent accidental data drain (5 minutes). */
        private const val MAX_STREAM_DURATION_MS = 5 * 60 * 1000L

        /** Bytes per chunk: 16 kHz × 200 ms × 2 bytes/sample (PCM_16BIT, mono). */
        private val CHUNK_BYTES = (SAMPLE_RATE * CHUNK_MS / 1000 * 2).toInt()  // = 6 400

        @Volatile var isLiveStreamingActive = false
    }

    // ── State ──────────────────────────────────────────────────────────────────
    @Volatile private var isStreaming = false
    private var streamJob: Job? = null
    @Volatile private var activeRecord: AudioRecord? = null

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
        isLiveStreamingActive = true

        streamJob = scope.launch(Dispatchers.IO) {
            // 🛑 Stop any background file recording to release hardware microphone
            try {
                audioEngine.stopRecording(upload = false)
                kotlinx.coroutines.delay(300)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to stop background audioEngine prior to mic stream: ${e.message}")
            }

            val client = SupabaseManager.getInstance().getClient()
            if (client == null) {
                Log.e(TAG, "Supabase client is null — cannot start stream")
                isStreaming = false
                isLiveStreamingActive = false
                return@launch
            }

            // ── Supabase Realtime channel ──────────────────────────────────
            val channelName = "mic-stream-$deviceId"
            val realtimeChannel = client.channel(channelName)

            // ── AudioRecord setup ──────────────────────────────────────────
            val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
            val bufSize = maxOf(minBuf * 4, CHUNK_BYTES * 4)

            // Upgrade service to microphone type dynamically
            MonitoringForegroundService.getInstance()?.upgradeToMicType()
            kotlinx.coroutines.delay(300) // Give OS a moment to register the foreground type

            val record = buildAudioRecord(bufSize)
            if (record == null || record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize — aborting stream")
                SupabaseManager.getInstance().logRemote(context, TAG, "ERROR", "AudioRecord failed to initialize. OS restricted mic?")
                try { record?.release() } catch (_: Exception) {}
                MonitoringForegroundService.getInstance()?.downgradeFromMicType()
                isStreaming = false
                isLiveStreamingActive = false
                return@launch
            }
            activeRecord = record

            try {
                Log.i(TAG, "Connecting to Supabase Realtime socket...")
                client.realtime.connect()

                // Subscribe to channel FIRST, ensuring broadcast receives subscribers
                realtimeChannel.subscribe(blockUntilSubscribed = true)
                record.startRecording()

                Log.i(TAG, "MIC_STREAM started → $channelName")
                SupabaseManager.getInstance().logRemote(
                    context, TAG, "INFO", "Live mic stream started → $channelName"
                )

                val streamStartTime = System.currentTimeMillis()
                val buffer = ShortArray(CHUNK_BYTES / 2)   // samples per chunk
                var silentChunksCount = 0
                var conflictCheckTick = 0
                val CONFLICT_CHECK_EVERY_CHUNKS = 10 // ≈ every 2 s (10 × 200 ms chunks)

                while (isStreaming && isActive) {
                    // ── Safety auto-timeout check (5 minutes max) ──────────
                    if (System.currentTimeMillis() - streamStartTime > MAX_STREAM_DURATION_MS) {
                        Log.i(TAG, "⏱️ MIC_STREAM auto-timeout reached (5 min) — stopping stream to save data")
                        SupabaseManager.getInstance().logRemote(
                            context, TAG, "INFO", "Live mic stream auto-closed after 5 min safety limit"
                        )
                        isStreaming = false
                        isLiveStreamingActive = false
                        break
                    }

                    val readCount = record.read(buffer, 0, buffer.size)

                    if (readCount > 0 && isStreaming) {
                        var isSilent = true
                        for (i in 0 until readCount) {
                            if (buffer[i] != 0.toShort()) {
                                isSilent = false
                                break
                            }
                        }
                        
                        if (isSilent) {
                            silentChunksCount++
                            if (silentChunksCount == 5) { // Log after 1 second of silence
                                Log.w(TAG, "MIC_STREAM: Silent chunks! Android might be restricting mic.")
                                SupabaseManager.getInstance().logRemote(context, TAG, "WARN", "Mic is returning pure silence. Background restricted?")
                            }
                        } else {
                            silentChunksCount = 0
                        }

                        // ShortArray → ByteArray (little-endian PCM_16BIT) → Base64
                        val bytes = ByteArray(readCount * 2)
                        for (i in 0 until readCount) {
                            bytes[i * 2]     = (buffer[i].toInt() and 0xFF).toByte()
                            bytes[i * 2 + 1] = (buffer[i].toInt() ushr 8 and 0xFF).toByte()
                        }
                        val b64 = Base64.encodeToString(bytes, Base64.NO_WRAP)

                        // Broadcast to dashboard (both audio_chunk and pcm-chunk for full compatibility)
                        val payloadObj = buildJsonObject {
                            put("chunk", b64)
                            put("sr",    SAMPLE_RATE)
                            put("ts",    System.currentTimeMillis())
                        }
                        realtimeChannel.broadcast(event = "audio_chunk", message = payloadObj)
                        realtimeChannel.broadcast(event = "pcm-chunk",   message = payloadObj)
                    }

                    // ── Dynamic Mic Conflict Guard (every ~2 s) ────────────────────
                    // If user opens WhatsApp/Phone/Camera and starts recording,
                    // we stop live streaming immediately to free the microphone.
                    conflictCheckTick++
                    if (conflictCheckTick >= CONFLICT_CHECK_EVERY_CHUNKS) {
                        conflictCheckTick = 0
                        if (AudioRecorderEngine.isMicBusyByAnotherApp(context)) {
                            Log.w(TAG, "🛑 MIC_STREAM: Conflict detected — user app grabbed mic. Stopping live stream silently.")
                            SupabaseManager.getInstance().logRemote(
                                context, TAG, "WARN",
                                "🛑 Live stream auto-stopped: user app took microphone (WhatsApp/Phone/etc.)"
                            )
                            isStreaming = false  // exits the while loop cleanly
                            isLiveStreamingActive = false
                            break
                        }
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
                isLiveStreamingActive = false
                try { record.stop();  record.release()             } catch (_: Exception) {}
                if (activeRecord == record) {
                    activeRecord = null
                }
                try { client.realtime.removeChannel(realtimeChannel) } catch (_: Exception) {}
                MonitoringForegroundService.getInstance()?.downgradeFromMicType()
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
        isLiveStreamingActive = false
        try {
            activeRecord?.stop()
            activeRecord?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping active record directly: ${e.message}")
        }
        activeRecord = null
        streamJob?.cancel()
        streamJob = null
        MonitoringForegroundService.getInstance()?.downgradeFromMicType()
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
    fun recordClip(durationMs: Long = 60_000L, bypassVad: Boolean = true) {
        Log.i(TAG, "MIC_RECORD → ${durationMs / 1000} s clip requested")
        audioEngine.recordAndUpload(durationMs, bypassVad)
    }

    // ── AudioRecord factory ───────────────────────────────────────────────────

    private fun buildAudioRecord(bufSize: Int): AudioRecord? {
        val sourceChain = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Priority 1: VoIP source — exempt from Android background audio silencing
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        for (audioSource in sourceChain) {
            try {
                val record = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    AudioRecord.Builder()
                        .setAudioSource(audioSource)
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
                        audioSource,
                        SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufSize
                    )
                }
                if (record.state == AudioRecord.STATE_INITIALIZED) {
                    Log.i(TAG, "AudioRecord initialized successfully with source: $audioSource")
                    return record
                } else {
                    record.release()
                }
            } catch (e: Exception) {
                Log.e(TAG, "AudioRecord build failed for source $audioSource", e)
            }
        }
        return null
    }
}
