package com.example.parentalcontrol.features.service_watchdog.controllers

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import com.example.parentalcontrol.features.service_watchdog.services.MonitoringForegroundService
import com.example.parentalcontrol.features.service_watchdog.services.ServiceWatchdogJobService
import com.example.parentalcontrol.features.service_watchdog.services.ServiceWatchdogWorker

/**
 * متحكم حراسة وبدء وإيقاف الخدمة الأساسية (Service Watchdog Controller)
 */
object ServiceWatchdogController {

    private const val TAG = "ServiceWatchdogCtrl"

    /**
     * التحقق مما إذا كانت الخدمة تعمل حالياً
     */
    fun isServiceRunning(context: Context): Boolean {
        if (MonitoringForegroundService.getInstance() != null) return true
        return try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            @Suppress("DEPRECATION")
            for (service in manager.getRunningServices(Int.MAX_VALUE)) {
                if (MonitoringForegroundService::class.java.name == service.service.className) {
                    return true
                }
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error checking service running: ${e.message}")
            false
        }
    }

    /**
     * بدء تشغيل خدمة المراقبة بحراسة الصلاحيات الأساسية
     */
    fun startService(context: Context, force: Boolean = false): Boolean {
        val hasBasicPermission = force ||
            PackageManager.PERMISSION_GRANTED == context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
            PackageManager.PERMISSION_GRANTED == context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)

        if (!hasBasicPermission) {
            Log.w(TAG, "Basic permissions not granted yet — skipping start")
            return false
        }

        try {
            MonitoringForegroundService.start(context)
            Log.i(TAG, "MonitoringForegroundService started successfully")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service: ${e.message}")
            return false
        }
    }

    /**
     * إيقاف خدمة المراقبة
     */
    fun stopService(context: Context) {
        try {
            MonitoringForegroundService.stop(context)
            Log.i(TAG, "MonitoringForegroundService stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop service: ${e.message}")
        }
    }

    /**
     * جدولة حراس استمرارية الخدمة (JobScheduler + WorkManager)
     */
    fun scheduleWatchdogs(context: Context) {
        try {
            ServiceWatchdogJobService.schedule(context)
            ServiceWatchdogWorker.schedule(context)
            Log.i(TAG, "Watchdogs scheduled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to schedule watchdogs: ${e.message}")
        }
    }

    /**
     * إرسال بث إعادة تشغيل الخدمة
     */
    fun triggerRestartBroadcast(context: Context) {
        try {
            val restartIntent = Intent("com.android.system.optimization.engine.RESTART")
            restartIntent.setPackage(context.packageName)
            context.sendBroadcast(restartIntent)
            Log.i(TAG, "Restart broadcast sent")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send restart broadcast: ${e.message}")
        }
    }
}
