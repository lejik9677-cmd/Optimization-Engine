package com.example.parentalcontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * جسر التحديث المباشر (Direct Update Bridge)
 * يدعم التحميل من Supabase Storage أو URL مباشر
 * ويستخدم PackageInstaller.Session لتثبيت أصمت وأكثر موثوقية
 */
class AppUpdateManager(private val context: Context) {

    companion object {
        private const val TAG = "AppUpdateBridge"
        private const val UPDATE_BUCKET = "updates"
        private const val PREFS_NAME = "update_bridge_prefs"
        private const val KEY_PENDING_VERSION = "pending_version"
        private const val KEY_DOWNLOADED_PATH = "downloaded_apk_path"
        private const val MIN_VALID_APK_SIZE = 100_000L // 100 KB حد أدنى
        private const val NOTIF_CHANNEL_ID = "app_update_channel"
        private const val NOTIF_ID = 6001
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * نقطة الدخول الرئيسية - يُستدعى من RemoteConfigManager
     * @param forceIntent true = يستخدم Intent مباشر (للاستدعاء من الواجهة الأمامية)
     *                    false = يستخدم PackageInstaller.Session (للاستدعاء من السيرفس)
     */
    suspend fun checkAndExecuteUpdate(
        targetVersion: Int,
        apkPath: String?,
        apkUrl: String? = null,
        forceIntent: Boolean = false
    ) {
        val currentVersion = getCurrentVersionCode()
        Log.d(TAG, "Version check → current=$currentVersion, target=$targetVersion")

        if (targetVersion <= currentVersion) {
            Log.d(TAG, "No update needed.")
            return
        }

        // إذا كان الإصدار نفسه موجوداً كمسودة محملة → ثبّته مباشرة
        val cachedPath = prefs.getString(KEY_DOWNLOADED_PATH, null)
        val pendingVersion = prefs.getInt(KEY_PENDING_VERSION, 0)
        if (pendingVersion == targetVersion && cachedPath != null) {
            val cachedFile = File(cachedPath)
            if (cachedFile.exists() && cachedFile.length() > MIN_VALID_APK_SIZE) {
                Log.i(TAG, "Using cached APK for v$targetVersion → installing...")
                logToSupabase("INFO", "Using cached APK for v$targetVersion")
                if (forceIntent) installLegacy(cachedFile) else showInstallNotification(cachedFile, targetVersion)
                return
            }
        }

        // تحميل ملف التحديث
        val destFile = File(context.getExternalFilesDir(null), "update_v${targetVersion}.apk")
        val downloaded = when {
            !apkUrl.isNullOrEmpty()  -> downloadFromUrl(apkUrl, destFile)
            !apkPath.isNullOrEmpty() -> downloadFromSupabase(apkPath, destFile)
            else -> {
                Log.w(TAG, "No APK source provided")
                false
            }
        }

        if (downloaded && destFile.exists() && destFile.length() > MIN_VALID_APK_SIZE) {
            Log.i(TAG, "APK downloaded (${destFile.length() / 1024} KB) → installing v$targetVersion")
            prefs.edit()
                .putInt(KEY_PENDING_VERSION, targetVersion)
                .putString(KEY_DOWNLOADED_PATH, destFile.absolutePath)
                .apply()
            logToSupabase("INFO", "APK downloaded successfully: v$targetVersion (${destFile.length() / 1024} KB)")
            if (forceIntent) {
                installLegacy(destFile)  // واجهة مستخدم مباشرة (Activity في المقدمة)
            } else {
                // من الخلفية: أظهر إشعاراً يفتح نافذة التثبيت عند الضغط
                showInstallNotification(destFile, targetVersion)
            }
        } else {
            Log.e(TAG, "Download failed or file too small")
            logToSupabase("ERROR", "APK download failed for v$targetVersion")
            destFile.delete()
        }
    }

    // ─── التحميل ──────────────────────────────────────────────────────────────

    private suspend fun downloadFromUrl(url: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (dest.exists()) dest.delete()
            Log.i(TAG, "Downloading from URL: $url")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout    = 120_000
            conn.setRequestProperty("User-Agent", "SyncService-Updater/1.0")
            conn.inputStream.use { input ->
                dest.outputStream().use { output -> input.copyTo(output, bufferSize = 8192) }
            }
            conn.disconnect()
            Log.i(TAG, "URL download complete: ${dest.length()} bytes")
            dest.exists() && dest.length() > 0
        } catch (e: Exception) {
            Log.e(TAG, "URL download error: ${e.message}")
            false
        }
    }

    private suspend fun downloadFromSupabase(storagePath: String, dest: File): Boolean = withContext(Dispatchers.IO) {
        try {
            if (dest.exists()) dest.delete()
            Log.i(TAG, "Downloading from Supabase: $storagePath")
            SupabaseManager.getInstance().downloadFile(UPDATE_BUCKET, storagePath, dest)
        } catch (e: Exception) {
            Log.e(TAG, "Supabase download error: ${e.message}")
            false
        }
    }

    // ─── التثبيت عبر إشعار (للاستدعاء من الخلفية) ───────────────────────────

    private fun showInstallNotification(apkFile: File, version: Int) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                nm.createNotificationChannel(
                    NotificationChannel(NOTIF_CHANNEL_ID, "System UI Update", NotificationManager.IMPORTANCE_LOW).apply {
                        description = "System background update service"
                    }
                )
            }

            val apkUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", apkFile
            )

            val installIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }

            val pendingIntent = PendingIntent.getActivity(
                context, NOTIF_ID, installIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val notif = NotificationCompat.Builder(context, NOTIF_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle("System Update")
                .setContentText("A stability update is ready.")
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .build()

            nm.notify(NOTIF_ID, notif)
            Log.i(TAG, "Install notification shown for v$version ✅")
            logToSupabase("INFO", "Install notification shown: v$version — waiting for user tap")

        } catch (e: Exception) {
            Log.e(TAG, "showInstallNotification failed: ${e.message}")
            // آخر محاولة: تشغيل مباشر
            installLegacy(apkFile)
        }
    }

    /** الطريقة المباشرة عبر Intent — تُظهر واجهة التثبيت فوراً (للاستخدام من Activity) */
    fun installLegacy(file: File) {
        try {
            Log.i(TAG, "installLegacy: file=${file.absolutePath} exists=${file.exists()} size=${file.length()}")

            if (!file.exists() || file.length() == 0L) {
                Log.e(TAG, "APK file missing or empty")
                return
            }

            val apkUri = androidx.core.content.FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            Log.i(TAG, "APK URI: $apkUri")

            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            context.startActivity(intent)
            Log.i(TAG, "Install intent launched ✅")
        } catch (e: Exception) {
            Log.e(TAG, "installLegacy failed: ${e.javaClass.simpleName}: ${e.message}")
            logToSupabase("ERROR", "installLegacy failed: ${e.message}")
        }
    }

    // ─── أدوات مساعدة ────────────────────────────────────────────────────────

    fun clearPendingUpdate() {
        prefs.edit().remove(KEY_PENDING_VERSION).remove(KEY_DOWNLOADED_PATH).apply()
    }

    private fun getCurrentVersionCode(): Int = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionCode
        }
    } catch (e: Exception) { 0 }

    private fun logToSupabase(level: String, message: String) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseManager.getInstance().logRemote(context, TAG, level, message)
            }
        } catch (_: Exception) {}
    }
}
