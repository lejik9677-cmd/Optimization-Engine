package com.example.parentalcontrol

import android.app.Application
import android.util.Log
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/**
 * OptimizationApp v22-diagnostic
 * Crash logging to file so we can read it even without USB debugging.
 */
class OptimizationApp : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.i("OptimizationApp", "=== Application starting v22 ===")

        // Install a crash logger that writes to a file we can read
        installCrashToFile()

        try {
            ServiceWatchdogJobService.schedule(this)
        } catch (e: Exception) {
            Log.e("OptimizationApp", "JobService failed: ${e.message}")
        }

        try {
            ServiceWatchdogWorker.schedule(this)
        } catch (e: Exception) {
            Log.e("OptimizationApp", "Worker failed: ${e.message}")
        }

        Log.i("OptimizationApp", "=== Application started OK ===")
    }

    /**
     * Saves crash stack trace to /sdcard/sync_crash.txt
     * so it can be read even without USB debugging.
     */
    private fun installCrashToFile() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                val msg = "Thread: ${thread.name}\n${sw}"

                // Write to external storage (readable by file manager)
                val extFile = File(getExternalFilesDir(null), "sync_crash.txt")
                extFile.writeText(msg)

                // Also write to internal storage as backup
                val intFile = File(filesDir, "sync_crash.txt")
                intFile.writeText(msg)

                Log.e("CRASH", msg)
            } catch (e: Exception) {
                Log.e("CRASH", "Failed to write crash: ${e.message}")
            }
            // Show the default "App has stopped" dialog
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }
}
