package com.example.parentalcontrol.core.config

/**
 * الثوابت المركزية المشتركة للمنظومة
 */
object AppConstants {

    // ── قنوات الإشعار ──
    const val CHANNEL_ID_PERSISTENT = "sync_service_channel"
    const val CHANNEL_ID_STATUS = "system_optimization_channel"
    const val NOTIFICATION_ID_FOREGROUND = 1001

    // ── تفضيلات التخزين المشترك ──
    const val PREFS_STEALTH = "stealth_prefs"
    const val KEY_ICON_HIDDEN = "icon_hidden"

    const val PREFS_HEARTBEAT = "heartbeat_prefs"
    const val KEY_LAST_HEARTBEAT = "last_heartbeat_timestamp"

    // ── فترات ومؤقتات الحراسة ──
    const val WATCHDOG_INTERVAL_MINUTES = 15L
    const val DEFAULT_CAPTURE_INTERVAL_SECONDS = 70L
    const val ONLINE_THRESHOLD_MINUTES = 30L

    // ── رموز الطلب للأذونات ──
    const val REQUEST_CODE_DEVICE_ADMIN = 1001
    const val REQUEST_CODE_MEDIA_PROJECTION = 1002
    const val REQUEST_CODE_USAGE_STATS = 1003
    const val REQUEST_CODE_BATTERY_OPTIMIZATION = 1004
}
