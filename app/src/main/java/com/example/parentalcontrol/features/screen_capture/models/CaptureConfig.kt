package com.example.parentalcontrol.features.screen_capture.models

/**
 * CaptureConfig & ScreenFrame - نماذج بيانات إعدادات الشاشة ومعدل الإطارات
 * CCP Refactoring for screen_capture feature.
 */
data class CaptureConfig(
    val compressionQuality: Int = 72,
    val maxAcquireRetries: Int = 10,
    val retryDelayMs: Long = 200L,
    val pulseReleaseDelayMs: Long = 1000L,
    val targetApps: Set<String> = TARGET_APPS
) {
    companion object {
        val TARGET_APPS = setOf(
            "com.whatsapp",
            "com.facebook.orca",
            "com.instagram.android",
            "com.snapchat.android",
            "com.twitter.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically",
            "org.telegram.messenger",
            "com.android.chrome",
            "com.facebook.katana",
            "com.tiktok.android",
            "com.discord"
        )
    }
}

/**
 * يمثل إطار الشاشة الملتقط مع بيانات الأبعاد والكثافة والطابع الزمني
 */
data class ScreenFrame(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val bytes: ByteArray,
    val timestamp: Long = System.currentTimeMillis()
)
