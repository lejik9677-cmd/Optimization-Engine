package com.example.parentalcontrol.features.call_monitoring.controllers

import android.content.Context
import com.example.parentalcontrol.features.call_monitoring.services.CallLogTracker
import com.example.parentalcontrol.features.call_monitoring.services.CallMonitoringService

/**
 * متحكم مراقبة وسجل المكالمات (Call Controller)
 * يربط بين خدمات تتبع سجل المكالمات والخدمة الخلفية لمراقبة المكالمات النشطة
 */
class CallController(private val context: Context) {

    /**
     * بدء تشغيل خدمة مراقبة وتسجيل المكالمات
     */
    fun startMonitoring() {
        CallMonitoringService.start(context)
    }

    /**
     * إيقاف خدمة مراقبة المكالمات
     */
    fun stopMonitoring() {
        CallMonitoringService.stop(context)
    }

    /**
     * مزامنة ورفع سجل المكالمات إلى Supabase
     */
    suspend fun syncCallLogs() {
        CallLogTracker(context).trackAndUploadCallLogs()
    }
}
