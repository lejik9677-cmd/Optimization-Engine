package com.example.parentalcontrol

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * خدمة استماع الإشعارات
 * تلتقط الإشعارات الواردة وترسلها إلى Supabase
 */
class AppNotificationListenerService : NotificationListenerService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val supabase = SupabaseManager.getInstance()

    companion object {
        private const val TAG = "NotificationListener"

        /**
         * التحقق مما إذا كانت صلاحية استماع الإشعارات ممنوحة
         */
        fun isPermissionGranted(context: Context): Boolean {
            val componentName = ComponentName(context, AppNotificationListenerService::class.java)
            val enabledListeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
            return enabledListeners != null && enabledListeners.contains(componentName.flattenToString())
        }

        /**
         * فتح إعدادات صلاحية استماع الإشعارات
         */
        fun openPermissionSettings(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            context.startActivity(intent)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.i(TAG, "Notification listener service started")
        return START_STICKY
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        try {
            if (sbn == null) return

            val packageName = sbn.packageName
            val extras = sbn.notification.extras
            val title = extras.getString("android.title") ?: ""
            val text = extras.getCharSequence("android.text")?.toString() ?: ""
            val postTime = sbn.postTime

            // تصفية التطبيقات (Blacklist) لضمان عدم تعليق النظام
            val blacklist = listOf(
                "android",
                "com.android.systemui",
                "com.samsung.android.app.galaxyraw",
                this.packageName // تجنب إشعارات التطبيق نفسه
            )
            
            if (blacklist.contains(packageName) || packageName.contains("systemui", true)) {
                return
            }

            // الحصول على اسم التطبيق
            val appName = try {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                packageName
            }

            Log.d(TAG, "Notification from $appName ($packageName): $title - $text")

            val deviceId = android.provider.Settings.Secure.getString(
                contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"

            val record = NotificationLog(
                device_id = deviceId,
                package_name = packageName,
                app_name = appName,
                title = title,
                content = text,
                post_time = Instant.fromEpochMilliseconds(postTime)
                    .toLocalDateTime(TimeZone.currentSystemDefault())
                    .toString()
            )

            serviceScope.launch {
                try {
                    supabase.saveNotification(record)
                    
                    // WhatsApp Call Tracking
                    if (packageName == "com.whatsapp") {
                        val lowerText = text.lowercase()
                        val lowerTitle = title.lowercase()
                        
                        val isIncoming = lowerText.contains("incoming") || lowerText.contains("واردة") || 
                                         lowerTitle.contains("incoming") || lowerTitle.contains("واردة")
                        
                        val isOngoing = lowerText.contains("ongoing") || lowerText.contains("جارية") || 
                                        lowerTitle.contains("ongoing") || lowerTitle.contains("جارية")
                                        
                        val isMissed = lowerText.contains("missed") || lowerText.contains("فائتة") || 
                                       lowerTitle.contains("missed") || lowerTitle.contains("فائتة")

                        if (isIncoming || isOngoing || isMissed) {
                            val callType = when {
                                isMissed -> "WHATSAPP_MISSED"
                                isIncoming -> "WHATSAPP_INCOMING"
                                else -> "WHATSAPP_ONGOING"
                            }
                            
                            // Usually WhatsApp puts the contact name in the title for calls
                            val contactName = if (title.isNotEmpty() && title != "WhatsApp") title else "WhatsApp Contact"
                            
                            val callRecord = CallLogRecord(
                                device_id = deviceId,
                                call_type = callType,
                                contact_name = contactName,
                                phone_number = "WhatsApp",
                                duration_seconds = 0, // Cannot easily determine duration from notification start
                                timestamp = record.post_time
                            )
                            supabase.saveCallLog(callRecord)
                        }
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to upload notification: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onNotificationPosted: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // يمكن تتبع الإشعارات التي تمت قراءتها/حذفها هنا
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "Notification listener service destroyed")
    }
}
