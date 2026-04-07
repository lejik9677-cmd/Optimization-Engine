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

                        // تحديث الحالة وتنفيذ الأوامر
                        for (cmd in newCommands) {
                            try {
                                // 1. تعيين الحالة إلى قيد التنفيذ لمنع التكرار
                                client.postgrest[TABLE_NAME].update(
                                    {
                                        set("status", "EXECUTED")
                                    }
                                ) {
                                    filter { eq("id", cmd.id) }
                                }

                                Log.i(TAG, "Executing command: \${cmd.command}")
                                executeCommand(cmd.command.uppercase().trim())
                            } catch (e: Exception) {
                                Log.e(TAG, "Failed to process command \${cmd.id}: \${e.message}")
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Polling error: \${e.message}")
                }
                
                // انتظار 5 ثوانٍ قبل الفحص التالي (استهلاك منخفض جداً للطاقة)
                delay(5000L)
            }
        }
    }

    private fun executeCommand(command: String) {
        try {
            when (command) {
                "LOCK" -> parentalControl.lockScreen()
                "WIPE" -> parentalControl.wipeData(false)
                "ALARM" -> playAlarm()
                "CAPTURE" -> scope.launch(Dispatchers.IO) {
                    val projection = projectionProvider()
                    if (projection != null) {
                        screenCapture.captureAndUpload(projection, forceCapture = true)
                    } else {
                        Log.e(TAG, "Cannot capture screen: MediaProjection is null")
                    }
                }
                "RECORD" -> scope.launch(Dispatchers.IO) {
                    audioRecorder.recordAndUpload(30000L) // تسجيل 30 ثانية
                }
                else -> Log.w(TAG, "Unknown command received: \$command")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing command: \${e.message}")
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
                    Log.e(TAG, "Error stopping alarm: \${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm: \${e.message}")
        }
    }
}
