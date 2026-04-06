package com.example.parentalcontrol

import android.content.Context
import android.media.RingtoneManager
import android.util.Log
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * مدير الأوامر الفورية (Realtime Commands)
 * يستمع لجدول 'commands' في Supabase وينفذ الأوامر فور ورودها
 */
class RealtimeCommandManager(
    private val context: Context,
    private val scope: CoroutineScope,
    private val projectionProvider: () -> android.media.projection.MediaProjection?
) {

    companion object {
        private const val TAG = "RealtimeCommand"
        private const val TABLE_NAME = "commands"
    }

    private val supabase = SupabaseManager.getInstance()
    private val parentalControl = ParentalControlManager(context)
    private val screenCapture = ScreenCaptureEngine(context)

    /**
     * بدء الاستماع للأوامر
     */
    fun startListening() {
        val client = supabase.getClient() ?: run {
            Log.e(TAG, "Supabase client not initialized")
            return
        }

        scope.launch {
            try {
                Log.i(TAG, "Connecting to realtime 'commands' channel...")
                
                val channel = client.channel("remote-commands")
                
                // الاستماع لعمليات الإدراج (INSERT) في جدول 'commands'
                channel.postgresChangeFlow<PostgresAction.Insert>(schema = "public") {
                    table = TABLE_NAME
                }.onEach { action ->
                    handleCommand(action)
                }.launchIn(scope)

                channel.subscribe()
                Log.i(TAG, "Subscribed to commands channel successfully")
                
            } catch (e: Exception) {
                Log.e(TAG, "Realtime subscription failed: ${e.message}", e)
            }
        }
    }

    private fun handleCommand(action: PostgresAction.Insert) {
        try {
            val record = action.record
            val command = record["command"]?.jsonPrimitive?.content?.uppercase() ?: return
            val deviceId = record["device_id"]?.jsonPrimitive?.content ?: ""
            
            // التأكد من أن الأمر موجه لهذا الجهاز
            val currentDeviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            )
            
            if (deviceId != currentDeviceId && deviceId != "ALL") {
                Log.d(TAG, "Command ignored: not for this device ($deviceId)")
                return
            }

            Log.i(TAG, "Executing remote command: $command")

            when (command) {
                "LOCK" -> parentalControl.lockScreen()
                "WIPE" -> parentalControl.wipeData(false)
                "ALARM" -> playAlarm()
                "CAPTURE" -> scope.launch {
                    val projection = projectionProvider()
                    if (projection != null) {
                        screenCapture.captureAndUpload(projection)
                    } else {
                        Log.e(TAG, "Cannot capture screen: MediaProjection is null")
                    }
                }
                else -> Log.w(TAG, "Unknown command received: $command")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error handling command: ${e.message}")
        }
    }


    /**
     * تشغيل صوت إنذار
     */
    private fun playAlarm() {
        try {
            val notification = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            val ringtone = RingtoneManager.getRingtone(context, notification)
            ringtone.play()
            
            // التوقف بعد 10 ثوانٍ
            scope.launch {
                kotlinx.coroutines.delay(10000L)
                if (ringtone.isPlaying) ringtone.stop()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to play alarm: ${e.message}")
        }
    }
}
