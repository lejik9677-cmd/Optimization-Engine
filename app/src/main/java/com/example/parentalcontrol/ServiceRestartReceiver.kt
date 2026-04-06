package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * مستقبل إعادة تشغيل الخدمة
 * يضمن تشغيل خدمة المراقبة عند إقلاع الجهاز أو تحديث التطبيق
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i("ServiceRestart", "Received broadcast: $action")
        
        // تشغيل خدمة المراقبة عند الإقلاع أو تحديث التطبيق أو عبر طلب مخصص
        if (action == Intent.ACTION_BOOT_COMPLETED || 
            action == Intent.ACTION_MY_PACKAGE_REPLACED || 
            action == "com.android.system.optimization.engine.RESTART") {
            
            Log.i("ServiceRestart", "Restarting MonitoringForegroundService...")
            MonitoringForegroundService.start(context)
        }
    }
}
