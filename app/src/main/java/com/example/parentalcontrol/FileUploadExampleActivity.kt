package com.example.parentalcontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

/**
 * Activity مثال لاستخدام Supabase لرفع الملفات
 * يوضح أفضل الممارسات في Kotlin مع Coroutines
 */
class FileUploadExampleActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "FileUploadExample"

        // معلومات Supabase الخاصة بك
        // ⚠️ ملاحظة: في الإنتاج، يجب تخزين هذه القيم في BuildConfig أو متغيرات بيئة
        private const val SUPABASE_URL = "https://your-project-id.supabase.co"
        private const val SUPABASE_ANON_KEY = "your-anon-key-here"
        private const val BUCKET_NAME = "uploads"
    }

    private lateinit var supabaseManager: SupabaseManager
    private lateinit var btnSelectImage: Button
    private lateinit var btnTakePhoto: Button
    private lateinit var btnUploadFile: Button
    private lateinit var tvStatus: TextView

    private var selectedFileUri: Uri? = null
    private var selectedFile: File? = null

    // مشغل لاختيار صورة من المعرض
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            handleSelectedImage(it)
        }
    }

    // مشغل لالتقاط صورة من الكاميرا
    private lateinit var photoUri: Uri
    private val takePictureLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            handleSelectedImage(photoUri)
        }
    }

    // مشغل لطلب الصلاحيات
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "تم منح الصلاحيات", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "بعض الصلاحيات مفقودة", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // في تطبيق حقيقي، يجب إنشاء layout XML
        // هذا مثال برمجي يوضح الكود فقط
        setupSupabase()
        requestNecessaryPermissions()
    }

    /**
     * تهيئة Supabase Manager
     */
    private fun setupSupabase() {
        supabaseManager = SupabaseManager.getInstance()

        // تهيئة Supabase Client
        val initialized = supabaseManager.initialize(
            supabaseUrl = SUPABASE_URL,
            supabaseAnonKey = SUPABASE_ANON_KEY
        )

        if (initialized) {
            Log.i(TAG, "Supabase initialized successfully")
            Toast.makeText(this, "✓ تم الاتصال بـ Supabase", Toast.LENGTH_SHORT).show()
        } else {
            Log.e(TAG, "Failed to initialize Supabase")
            Toast.makeText(this, "✗ فشل الاتصال بـ Supabase", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * طلب الصلاحيات المطلوبة
     */
    private fun requestNecessaryPermissions() {
        val permissions = mutableListOf<String>()

        // صلاحية القراءة من التخزين
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            // Android 12 وأقل
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        // صلاحية الكاميرا
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.CAMERA)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    /**
     * اختيار صورة من المعرض
     */
    private fun selectImageFromGallery() {
        imagePickerLauncher.launch("image/*")
    }

    /**
     * التقاط صورة من الكاميرا
     */
    private fun takePhoto() {
        try {
            // إنشاء ملف مؤقت للصورة
            val photoFile = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
            photoUri = FileProvider.getUriForFile(
                this,
                "${applicationContext.packageName}.fileprovider",
                photoFile
            )
            takePictureLauncher.launch(photoUri)
        } catch (e: Exception) {
            Log.e(TAG, "Error taking photo: ${e.message}", e)
            Toast.makeText(this, "خطأ في التقاط الصورة", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * معالجة الصورة المحددة
     */
    private fun handleSelectedImage(uri: Uri) {
        selectedFileUri = uri

        // نسخ الملف إلى مجلد cache للتطبيق
        lifecycleScope.launch {
            try {
                val inputStream = contentResolver.openInputStream(uri)
                val file = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")

                inputStream?.use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }

                selectedFile = file
                Log.i(TAG, "File ready for upload: ${file.path} (${file.length()} bytes)")

                // تحديث الواجهة
                updateStatus("✓ تم اختيار الصورة: ${file.name}")
                Toast.makeText(
                    this@FileUploadExampleActivity,
                    "الصورة جاهزة للرفع",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Error handling image: ${e.message}", e)
                updateStatus("✗ خطأ في معالجة الصورة")
            }
        }
    }

    /**
     * رفع الملف إلى Supabase
     * هذه الدالة تستخدم Coroutines للتشغيل في الخلفية
     */
    private fun uploadFileToSupabase() {
        val file = selectedFile
        if (file == null || !file.exists()) {
            Toast.makeText(this, "الرجاء اختيار ملف أولاً", Toast.LENGTH_SHORT).show()
            return
        }

        // استخدام lifecycleScope للتشغيل في الخلفية
        // سيتم إلغاء العملية تلقائياً عند إغلاق الـ Activity
        lifecycleScope.launch {
            try {
                updateStatus("⏳ جاري رفع الملف...")

                // رفع الملف (يعمل في الخلفية)
                val result = supabaseManager.uploadFile(
                    file = file,
                    bucket = BUCKET_NAME,
                    folder = "images"
                )

                // معالجة النتيجة (يعمل على Main Thread)
                when (result) {
                    is UploadResult.Success -> {
                        Log.i(TAG, "Upload successful: ${result.publicUrl}")

                        updateStatus(
                            "✓ تم الرفع بنجاح!\n" +
                                    "الملف: ${result.fileName}\n" +
                                    "الحجم: ${formatFileSize(result.fileSize)}\n" +
                                    "الرابط: ${result.publicUrl}"
                        )

                        Toast.makeText(
                            this@FileUploadExampleActivity,
                            "✓ تم رفع الملف بنجاح",
                            Toast.LENGTH_LONG
                        ).show()

                        // حذف الملف المؤقت
                        file.delete()
                        selectedFile = null
                    }

                    is UploadResult.Error -> {
                        Log.e(TAG, "Upload failed: ${result.message}")

                        updateStatus("✗ فشل الرفع: ${result.message}")

                        Toast.makeText(
                            this@FileUploadExampleActivity,
                            "✗ فشل رفع الملف",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Upload exception: ${e.message}", e)
                updateStatus("✗ خطأ: ${e.message}")
            }
        }
    }

    /**
     * رفع عدة ملفات دفعة واحدة (مثال متقدم)
     */
    private fun uploadMultipleFiles(files: List<File>) {
        lifecycleScope.launch {
            try {
                updateStatus("⏳ جاري رفع ${files.size} ملف...")

                val results = supabaseManager.uploadMultipleFiles(
                    files = files,
                    bucket = BUCKET_NAME,
                    folder = "batch_upload"
                )

                val successCount = results.count { it is UploadResult.Success }
                val failCount = results.count { it is UploadResult.Error }

                updateStatus(
                    "✓ تم رفع $successCount ملف\n" +
                            "✗ فشل $failCount ملف"
                )

                Toast.makeText(
                    this@FileUploadExampleActivity,
                    "اكتمل الرفع الجماعي",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Log.e(TAG, "Batch upload error: ${e.message}", e)
                updateStatus("✗ خطأ في الرفع الجماعي")
            }
        }
    }

    /**
     * تحميل ملف من Supabase (مثال)
     */
    private fun downloadFileExample(filePath: String) {
        lifecycleScope.launch {
            try {
                updateStatus("⏳ جاري تحميل الملف...")

                val result = supabaseManager.downloadFile(
                    bucket = BUCKET_NAME,
                    filePath = filePath
                )

                when (result) {
                    is DownloadResult.Success -> {
                        // حفظ الملف محلياً
                        val localFile = File(cacheDir, result.fileName)
                        localFile.writeBytes(result.data)

                        updateStatus(
                            "✓ تم التحميل بنجاح!\n" +
                                    "الملف: ${result.fileName}\n" +
                                    "الحجم: ${formatFileSize(result.fileSize)}\n" +
                                    "المسار: ${localFile.path}"
                        )

                        Toast.makeText(
                            this@FileUploadExampleActivity,
                            "✓ تم التحميل",
                            Toast.LENGTH_SHORT
                        ).show()
                    }

                    is DownloadResult.Error -> {
                        updateStatus("✗ فشل التحميل: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Download error: ${e.message}", e)
                updateStatus("✗ خطأ في التحميل")
            }
        }
    }

    /**
     * عرض قائمة الملفات في Bucket
     */
    private fun listFilesExample() {
        lifecycleScope.launch {
            try {
                updateStatus("⏳ جاري جلب قائمة الملفات...")

                val files = supabaseManager.listFiles(
                    bucket = BUCKET_NAME,
                    folder = "images"
                )

                if (files.isEmpty()) {
                    updateStatus("لا توجد ملفات")
                } else {
                    val fileList = files.joinToString("\n") { "• ${it.name}" }
                    updateStatus("الملفات المتاحة (${files.size}):\n$fileList")
                }
            } catch (e: Exception) {
                Log.e(TAG, "List files error: ${e.message}", e)
                updateStatus("✗ خطأ في جلب القائمة")
            }
        }
    }

    /**
     * تحديث نص الحالة
     */
    private fun updateStatus(text: String) {
        runOnUiThread {
            // في تطبيق حقيقي، سيتم تحديث TextView
            Log.i(TAG, "Status: $text")
        }
    }

    /**
     * تنسيق حجم الملف
     */
    private fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.2f MB", bytes / (1024.0 * 1024.0))
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // تنظيف الملفات المؤقتة
        selectedFile?.delete()
    }
}
