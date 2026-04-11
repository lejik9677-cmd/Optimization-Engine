package com.example.parentalcontrol

import android.app.ActivityManager
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * Watchdog job that runs every 15 minutes to ensure the monitoring service is active.
 */
class ServiceWatchdogJobService : JobService() {
    
    companion object {
        private const val JOB_ID = 1001
        
        fun schedule(context: Context) {
            val jobScheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as JobScheduler
            val componentName = ComponentName(context, ServiceWatchdogJobService::class.java)
            val jobInfo = JobInfo.Builder(JOB_ID, componentName)
                .setPeriodic(15 * 60 * 1000) // 15 mins
                .setPersisted(true)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .build()
            
            jobScheduler.schedule(jobInfo)
            Log.i("ServiceWatchdog", "Scheduled watchdog job")
        }
    }

    override fun onStartJob(params: JobParameters?): Boolean {
        try {
            if (!isServiceRunning(this, MonitoringForegroundService::class.java)) {
                // Check if we have basic permissions before even trying
                val hasPermission = android.content.pm.PackageManager.PERMISSION_GRANTED ==
                        checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION)

                if (hasPermission) {
                    Log.i("ServiceWatchdog", "Service not running, restarting...")
                    val restartIntent = Intent(this, MonitoringForegroundService::class.java)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        try {
                            startForegroundService(restartIntent)
                        } catch (e: Exception) {
                            Log.e("ServiceWatchdog", "Background start blocked: ${e.message}")
                        }
                    } else {
                        startService(restartIntent)
                    }
                } else {
                    Log.w("ServiceWatchdog", "Missing permissions, skipping background start")
                }
            }
        } catch (e: Exception) {
            Log.e("ServiceWatchdog", "Job execution error: ${e.message}")
        }
        
        return false // Job finished, not long running
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        return true // Reschedule if failed
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
