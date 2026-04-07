package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.from

/**
 * مستقبل تنبيهات تغيير الشريحة (SIM Change Receiver)
 * يرسل تنبيه فوري للقاعدة في حال تم تبديل شريحة الـ SIM
 */
class SimChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimChangeReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.intent.action.SIM_STATE_CHANGED" || 
            intent.action == "android.intent.action.SERVICE_STATE") {
            
            val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
            val simState = tm.simState
            
            // نتحقق من حالة الشريحة
            if (simState == TelephonyManager.SIM_STATE_READY) {
                Log.i(TAG, "New SIM detected or SIM state ready")
                
                // إرسال التنبيه في الخلفية
                val deviceId = android.provider.Settings.Secure.getString(
                    context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown"

                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val supabase = SupabaseManager.getInstance()
                        val alertData = mapOf(
                            "device_id" to deviceId,
                            "type" to "SIM_EVENT",
                            "details" to "SIM card state changed to READY. Possible SIM swap detected.",
                            "operator" to (tm.simOperatorName ?: "unknown")
                        )
                        
                        // رفع التنبيه لجدول التنبيهات (سنستخدم جدول app_usage كمستودع مؤقت للأحداث)
                        supabase.getClient()?.from("device_events")?.insert(alertData)
                        Log.i(TAG, "SIM alert sent to Supabase")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send SIM alert: ${e.message}")
                    }
                }
            }
        }
    }
}
