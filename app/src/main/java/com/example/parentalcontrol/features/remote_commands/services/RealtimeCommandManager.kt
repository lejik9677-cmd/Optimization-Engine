package com.example.parentalcontrol.features.remote_commands.services

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.serialization.json.JsonElement
import com.example.parentalcontrol.features.location_tracking.services.GpsTracker
import com.example.parentalcontrol.features.remote_commands.controllers.CommandActionExecutor
import com.example.parentalcontrol.features.remote_commands.models.CommandRecord

/**
 * مدير الأوامر الفورية (Realtime Command Manager)
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
    private val actionExecutor = CommandActionExecutor(context, scope, gpsTracker, projectionProvider)

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
                        val currentDeviceId = Settings.Secure.getString(
                            context.contentResolver, Settings.Secure.ANDROID_ID
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

                        // تنفيذ الأوامر المكتشفة عبر منفذ الإجراءات
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
                                        set("executed_at", Clock.System.now().toString())
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

    private suspend fun executeCommand(command: String, payloadJson: JsonElement?): Boolean {
        return actionExecutor.execute(command, payloadJson)
    }
}
