package com.example.parentalcontrol

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * WorkManager worker that runs periodically to ensure the service is alive.
 * Third layer of persistence (Watchdog).
 */
class ServiceWatchdogWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "ServiceWatchdogWorker"
        private const val WORK_NAME = "SystemOptimizationWatchdog"

        fun schedule(context: Context) {
            val workRequest = PeriodicWorkRequestBuilder<ServiceWatchdogWorker>(
                15, TimeUnit.MINUTES // Minimum interval allowed by Android
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
            Log.i(TAG, "Watchdog worker scheduled")
        }
    }

    override suspend fun doWork(): Result {
        try {
            if (!isServiceRunning(applicationContext, MonitoringForegroundService::class.java)) {
                Log.i(TAG, "Service is NOT running! Restarting...")
                val intent = Intent(applicationContext, MonitoringForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    applicationContext.startForegroundService(intent)
                } else {
                    applicationContext.startService(intent)
                }
            } else {
                Log.d(TAG, "Service is healthy")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in watchdog: ${e.message}")
            return Result.retry()
        }
        return Result.success()
    }

    @Suppress("DEPRECATION")
    private fun isServiceRunning(context: Context, serviceClass: Class<*>): Boolean {
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        for (service in manager.getRunningServices(Int.MAX_VALUE)) {
            if (serviceClass.name == service.service.className) {
                return true
            }
        }
        return false
    }
}
