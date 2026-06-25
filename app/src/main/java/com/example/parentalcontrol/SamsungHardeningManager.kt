package com.example.parentalcontrol

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AlertDialog

/**
 * SamsungHardeningManager v16
 *
 * Handles Samsung One UI 7 / Knox 3.11 specific survivability fixes:
 *
 * 1. Deep Sleep bypass — Samsung's "Sleeping apps" and "Deep sleeping apps"
 *    lists will kill our service even with battery optimization disabled.
 *    We detect One UI 7 and open the exact setting.
 *
 * 2. Battery Saver guard — Detect when the device enters extreme saver mode
 *    and log a remote warning so the dashboard shows it.
 *
 * 3. Adaptive Battery nag — Samsung's "App battery usage" can classify
 *    the service as "Restricted". We guide the user to set it to "Unrestricted".
 *
 * Detection heuristic:
 *   - Manufacturer == "samsung" (case-insensitive)
 *   - Build.VERSION.SDK_INT >= 35 (Android 15 = One UI 7.x)
 *   - Or SDK >= 34 (Android 14 = One UI 6.x) as fallback
 */
object SamsungHardeningManager {

    private const val TAG = "SamsungHardening"

    // ── Detection ──────────────────────────────────────────────────────────────

    val isSamsung: Boolean get() =
        Build.MANUFACTURER.equals("samsung", ignoreCase = true)

    val isOneUI7: Boolean get() =
        isSamsung && Build.VERSION.SDK_INT >= 35     // Android 15

    val isOneUI6: Boolean get() =
        isSamsung && Build.VERSION.SDK_INT >= 34     // Android 14

    val isSamsungDevice: Boolean get() = isSamsung && Build.VERSION.SDK_INT >= 29

    // ── Public entry point ────────────────────────────────────────────────────

    /**
     * Run all Samsung-specific hardening checks.
     * Call from MainActivity.onCreate() or after all basic permissions are granted.
     */
    fun runHardeningChecks(context: Context) {
        if (!isSamsungDevice) return
        Log.i(TAG, "Running Samsung hardening checks (One UI ${getOneUIVersion()})")
        ensureBatteryOptimizationDisabled(context)
    }

    /**
     * Show a guided dialog to open Samsung's Adaptive Battery "Unrestricted" setting.
     * This is DIFFERENT from the standard Android battery optimization screen.
     * On One UI 7, call from a visible Activity context.
     */
    fun showSamsungBatteryOptimizationDialog(context: Context) {
        if (!isSamsungDevice) return

        AlertDialog.Builder(context)
            .setTitle("⚙️ حماية التطبيق من النوم العميق (Samsung)")
            .setMessage(
                buildString {
                    append("على أجهزة Samsung One UI ${getOneUIVersion()}، ")
                    append("يمكن للنظام إيقاف الخدمة تلقائياً عبر ميزة 'التطبيقات النائمة'.\n\n")
                    append("لضمان الاستمرارية، يرجى تطبيق الخطوات التالية:\n\n")
                    append("1️⃣ الإعدادات → العناية بالجهاز → البطارية\n")
                    append("2️⃣ ابحث عن 'Optimization Engine' في قائمة التطبيقات\n")
                    append("3️⃣ اضغط على التطبيق واختر 'غير مقيد' (Unrestricted)\n\n")
                    append("الخيار التلقائي أدناه سيفتح الشاشة الصحيحة مباشرة.")
                }
            )
            .setPositiveButton("🔋 فتح إعداد البطارية") { _, _ ->
                openSamsungBatterySettings(context)
            }
            .setNeutralButton("إعداد Android القياسي") { _, _ ->
                openStandardBatteryOptimization(context)
            }
            .setNegativeButton("تخطي", null)
            .show()
    }

    // ── Battery optimization ──────────────────────────────────────────────────

    /**
     * Check if battery optimization is already disabled.
     * If not — show the Samsung-specific dialog.
     */
    fun ensureBatteryOptimizationDisabled(context: Context) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            Log.w(TAG, "Battery optimization ACTIVE — showing Samsung bypass dialog")
            if (context is android.app.Activity) {
                showSamsungBatteryOptimizationDialog(context)
            } else {
                // From a non-Activity context (e.g. Service), log and open silently
                openStandardBatteryOptimization(context)
            }
        } else {
            Log.i(TAG, "Battery optimization already disabled ✅")
        }
    }

    /**
     * Open Samsung's own battery management screen.
     * Intent targets the Samsung DeviceCare "Battery Usage" activity.
     */
    private fun openSamsungBatterySettings(context: Context) {
        // Try Samsung-specific intents first (One UI 4+)
        val samsungIntents = listOf(
            Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.lool",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            },
            Intent().apply {
                component = android.content.ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.battery.ui.BatteryActivity"
                )
            },
            // Fallback: Android standard "App battery usage" (API 31+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                Intent(Settings.ACTION_APP_USAGE_SETTINGS)
            else null
        )

        for (intent in samsungIntents) {
            if (intent == null) continue
            try {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                Log.i(TAG, "Opened Samsung battery settings: ${intent.component}")
                return
            } catch (e: ActivityNotFoundException) {
                Log.d(TAG, "Intent not found: ${intent.component}")
            }
        }

        // Final fallback
        openStandardBatteryOptimization(context)
    }

    private fun openStandardBatteryOptimization(context: Context) {
        try {
            context.startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${context.packageName}")
                ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cannot open battery settings: ${e.message}")
            // Last resort: open app details
            try {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:${context.packageName}"))
                        .apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                )
            } catch (_: Exception) {}
        }
    }

    // ── Diagnostics ───────────────────────────────────────────────────────────

    /** Human-readable One UI version string */
    fun getOneUIVersion(): String = when {
        Build.VERSION.SDK_INT >= 35 -> "7"
        Build.VERSION.SDK_INT >= 34 -> "6"
        Build.VERSION.SDK_INT >= 33 -> "5"
        Build.VERSION.SDK_INT >= 31 -> "4"
        else -> "3 or earlier"
    }

    /** True when the device is in any battery-saver mode */
    fun isBatterySaverActive(context: Context): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return pm.isPowerSaveMode
    }

    /**
     * Build a diagnostic string for remote logging.
     * Call from MonitoringForegroundService.startRemoteConfigLoop() or heartbeat.
     */
    fun buildDiagnosticString(context: Context): String {
        if (!isSamsungDevice) return "Non-Samsung device"
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return buildString {
            append("Samsung One UI ${getOneUIVersion()} | ")
            append("API ${Build.VERSION.SDK_INT} | ")
            append("Model: ${Build.MODEL} | ")
            append("BattOpt: ${!pm.isIgnoringBatteryOptimizations(context.packageName)} | ")
            append("BatterySaver: ${pm.isPowerSaveMode}")
        }
    }
}
