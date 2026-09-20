package com.example.parentalcontrol.features.app_usage_and_notifications.controllers

import android.app.AppOpsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import android.util.Log
import com.example.parentalcontrol.features.app_usage_and_notifications.services.AppNotificationListenerService
import com.example.parentalcontrol.features.app_usage_and_notifications.services.AppUsageTracker

/**
 * NotificationUsageController — متحكم إدارة وفحص وتفعيل أذونات تتبع الاستخدام والوصول للإشعارات
 */
class NotificationUsageController(private val context: Context) {

    private val usageTracker = AppUsageTracker(context)

    companion object {
        private const val TAG = "NotificationUsageCtrl"

        /**
         * فحص ما إذا كانت صلاحية استماع الإشعارات ممنوحة
         */
        fun isNotificationPermissionGranted(context: Context): Boolean {
            return try {
                val componentName = ComponentName(context, AppNotificationListenerService::class.java)
                val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
                enabledListeners != null && enabledListeners.contains(componentName.flattenToString())
            } catch (e: Exception) {
                Log.e(TAG, "Error checking notification permission: ${e.message}")
                false
            }
        }

        /**
         * فتح نافذة إعدادات صلاحية استماع الإشعارات
         */
        fun openNotificationPermissionSettings(context: Context) {
            try {
                val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS").apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open notification settings: ${e.message}")
            }
        }

        /**
         * فحص صلاحية الوصول لبيانات استخدام التطبيقات (Usage Stats)
         */
        fun hasUsageAccessPermission(context: Context): Boolean {
            return try {
                val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                val mode = appOps.checkOpNoThrow(
                    AppOpsManager.OPSTR_GET_USAGE_STATS,
                    Process.myUid(),
                    context.packageName
                )
                mode == AppOpsManager.MODE_ALLOWED
            } catch (e: Exception) {
                Log.e(TAG, "Error checking usage stats permission: ${e.message}")
                false
            }
        }

        /**
         * فتح نافذة إعدادات صلاحية الوصول لبيانات الاستخدام
         */
        fun openUsageAccessSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open usage access settings: ${e.message}")
            }
        }
    }

    fun hasUsagePermission(): Boolean = hasUsageAccessPermission(context)

    fun isNotificationGranted(): Boolean = isNotificationPermissionGranted(context)

    suspend fun trackAndUploadUsage() = usageTracker.trackAndUploadUsage()
}
