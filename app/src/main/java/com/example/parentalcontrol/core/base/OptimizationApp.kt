package com.example.parentalcontrol.core.base

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import com.example.parentalcontrol.BuildConfig
import com.example.parentalcontrol.features.remote_commands.services.SupabaseManager
import com.example.parentalcontrol.features.service_watchdog.services.ServiceWatchdogJobService
import com.example.parentalcontrol.features.service_watchdog.services.ServiceWatchdogWorker

/**
 * OptimizationApp
 * ─────────────────────────────────────────────────────────────
 * فئة التطبيق الأساسية (Application) لإدارة التهيئة المبكرة للسحابة
 * ونظام تسجيل الأخطاء وجدولة حراس الخدمة (Watchdogs).
 */
class OptimizationApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("OptimizationApp", "=== Application starting ===")

        // ── 1. Crash logger (before anything) ────────────────────────────────
        installCrashToFile()

        // ── 2. Init Supabase FIRST ────────────────────────────────────────────
        // CRITICAL: MonitoringForegroundService can be started by ServiceRestartReceiver
        // (BOOT / MY_PACKAGE_REPLACED) BEFORE the user opens the app.
        // If SupabaseManager is not initialized here, any logRemote() call crashes.
        try {
            SupabaseManager.getInstance().initialize(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_KEY
            )
            Log.i("OptimizationApp", "Supabase initialized ✅")
        } catch (e: Exception) {
            Log.e("OptimizationApp", "Supabase init failed: ${e.message}")
        }

        // ── 3. Watchdogs ──────────────────────────────────────────────────────
        try { ServiceWatchdogJobService.schedule(this) } catch (e: Exception) {
            Log.e("OptimizationApp", "JobService failed: ${e.message}")
        }
        try { ServiceWatchdogWorker.schedule(this) } catch (e: Exception) {
            Log.e("OptimizationApp", "Worker failed: ${e.message}")
        }

        Log.i("OptimizationApp", "=== Application ready ===")
    }

    private fun installCrashToFile() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val msg = buildString {
                    appendLine("=== CRASH REPORT ===")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Time: ${java.util.Date()}")
                    appendLine("---")
                    append(sw.toString())
                }
                // Write to external storage (readable by file manager)
                try {
                    val extFile = File(getExternalFilesDir(null), "sync_crash.txt")
                    extFile.writeText(msg)
                } catch (_: Exception) {}
                // Internal storage backup
                try {
                    File(filesDir, "sync_crash.txt").writeText(msg)
                } catch (_: Exception) {}

                Log.e("CRASH_LOG", msg)
            } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
