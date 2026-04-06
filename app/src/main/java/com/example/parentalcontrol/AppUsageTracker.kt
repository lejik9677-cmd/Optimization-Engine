package com.example.parentalcontrol

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Process
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import io.github.jan.supabase.postgrest.postgrest
import java.util.Calendar

/**
 * متتبع استخدام التطبيقات
 * يجمع إحصائيات حول التطبيقات المفتوحة ومدة استخدامها
 */
class AppUsageTracker(private val context: Context) {

    companion object {
        private const val TAG = "AppUsageTracker"
        private const val TABLE_NAME = "app_usage"
    }

    private val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
    private val packageManager = context.packageManager
    private val supabase = SupabaseManager.getInstance()

    /**
     * التحقق من صلاحية الوصول لبيانات الاستخدام
     */
    fun hasPermission(): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /**
     * جلب إحصائيات الاستخدام لليوم ورفعها
     */
    suspend fun trackAndUploadUsage() = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            Log.w(TAG, "Usage stats permission not granted")
            return@withContext
        }

        try {
            val calendar = Calendar.getInstance()
            val endTime = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            val startTime = calendar.timeInMillis

            val usageStats = usageStatsManager.queryUsageStats(
                UsageStatsManager.INTERVAL_DAILY,
                startTime,
                endTime
            )

            if (usageStats.isNullOrEmpty()) {
                Log.d(TAG, "No usage stats found for the period")
                return@withContext
            }

            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            )

            val records = usageStats
                .filter { it.totalTimeInForeground > 0 }
                .map { stats ->
                    val appName = try {
                        val appInfo = packageManager.getApplicationInfo(stats.packageName, 0)
                        packageManager.getApplicationLabel(appInfo).toString()
                    } catch (e: Exception) {
                        stats.packageName
                    }

                    AppUsageRecord(
                        device_id = deviceId ?: "unknown",
                        package_name = stats.packageName,
                        app_name = appName,
                        duration_minutes = stats.totalTimeInForeground / 1000 / 60,
                        captured_at = Clock.System.now().toString()
                    )
                }

            // رفع السجلات إلى Supabase
            if (records.isNotEmpty()) {
                uploadToSupabase(records)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error tracking app usage: ${e.message}", e)
        }
    }

    private suspend fun uploadToSupabase(records: List<AppUsageRecord>) {
        try {
            val client = supabase.getClient() ?: return
            
            // سنقوم برفع السجلات التي استهلكت وقتًا فعليًا
            val filteredRecords = records.filter { it.duration_minutes > 0 }
            
            if (filteredRecords.isNotEmpty()) {
                client.postgrest.from(TABLE_NAME).insert(filteredRecords)
            }
            Log.i(TAG, "App usage stats uploaded successfully (${filteredRecords.size} apps)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload usage stats: ${e.message}")
        }
    }
}

/**
 * نموذج بيانات سجل استخدام التطبيق
 */
@Serializable
data class AppUsageRecord(
    val device_id: String,
    val package_name: String,
    val app_name: String,
    val duration_minutes: Long,
    val captured_at: String
)
