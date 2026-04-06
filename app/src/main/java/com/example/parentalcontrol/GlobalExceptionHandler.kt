package com.example.parentalcontrol

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import java.io.PrintWriter
import java.io.StringWriter
import kotlin.system.exitProcess

/**
 * Global error handler that silently reports crashes to Supabase and restarts the service.
 */
class GlobalExceptionHandler(
    private val context: Context,
    private val defaultHandler: Thread.UncaughtExceptionHandler?
) : Thread.UncaughtExceptionHandler {

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            val sw = StringWriter()
            throwable.printStackTrace(PrintWriter(sw))
            val stackTrace = sw.toString()

            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            val errorLog = ErrorLog(
                device_id = deviceId,
                error_message = throwable.message ?: "Unknown error",
                stack_trace = stackTrace,
                app_version = getAppVersion(),
                timestamp = Clock.System.now().toString()
            )

            // Send to Supabase (Blocking for a moment to ensure it sends)
            runBlocking {
                SupabaseManager.getInstance().saveErrorLog(errorLog)
            }

            // Silently restart the service
            val restartIntent = Intent(context, MonitoringForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(restartIntent)
            } else {
                context.startService(restartIntent)
            }

        } catch (e: Exception) {
            Log.e("GlobalExceptionHandler", "Failed to handle crash: ${e.message}")
        } finally {
            // End process without showing "App has stopped" dialog if possible
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(10)
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            packageInfo.versionName ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    companion object {
        fun initialize(context: Context) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler !is GlobalExceptionHandler) {
                Thread.setDefaultUncaughtExceptionHandler(GlobalExceptionHandler(context, currentHandler))
            }
        }
    }
}
