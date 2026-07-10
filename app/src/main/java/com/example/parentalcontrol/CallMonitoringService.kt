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

        // Guard: READ_PHONE_STATE is a runtime permission — don't crash if not granted
        val hasPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
            checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)

        if (hasPermission) {
            registerCallListener()
            Log.i(TAG, "CallMonitoringService created — listening for call events")
        } else {
            Log.w(TAG, "READ_PHONE_STATE not granted — call monitoring disabled")
            stopSelf()  // Stop immediately, don't crash
        }
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
                // logRemote is suspend → must be called inside a coroutine
                scope.launch {
                    SupabaseManager.getInstance()
                        .logRemote(this@CallMonitoringService, TAG, "INFO",
                            "Call detected \u2192 starting audio recorder")
                }
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
        val service = MonitoringForegroundService.getInstance()
        if (service != null) {
            val started = service.startCallRecording()
            if (started) {
                isRecording = true
                Log.i(TAG, "Call recording delegated to MonitoringForegroundService")
            } else {
                Log.e(TAG, "Failed to start call recording via MonitoringForegroundService")
            }
        } else {
            Log.e(TAG, "MonitoringForegroundService instance is null")
        }
    }

    private fun stopAndUpload() {
        if (!isRecording) return
        val service = MonitoringForegroundService.getInstance()
        if (service != null) {
            service.stopCallRecording()
            Log.i(TAG, "Call recording stop delegated to MonitoringForegroundService")
        }
        isRecording = false
    }
}
