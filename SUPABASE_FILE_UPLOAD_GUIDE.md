# دليل رفع الملفات باستخدام Supabase - File Upload Module

## 📋 نظرة عامة

هذا الدليل يشرح كيفية استخدام وحدة رفع الملفات (File Upload Module) المدمجة مع Supabase في مشروع Android.

## 🔧 المتطلبات الأساسية

### 1. إنشاء مشروع Supabase

1. اذهب إلى [Supabase Dashboard](https://app.supabase.com)
2. قم بإنشاء مشروع جديد
3. احصل على:
   - **Project URL**: `https://xxxxx.supabase.co`
   - **Anon Key**: المفتاح العام (من Settings → API)

### 2. إنشاء Storage Bucket

في Supabase Dashboard:

```sql
-- اذهب إلى Storage → Create Bucket
-- الاسم: "uploads"
-- Public: true (إذا كنت تريد روابط عامة)
```

أو باستخدام SQL:

```sql
-- إنشاء bucket
INSERT INTO storage.buckets (id, name, public)
VALUES ('uploads', 'uploads', true);

-- السماح برفع الملفات (سياسة RLS)
CREATE POLICY "Allow public uploads"
ON storage.objects FOR INSERT
TO public
WITH CHECK (bucket_id = 'uploads');

-- السماح بقراءة الملفات
CREATE POLICY "Allow public reads"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'uploads');
```

## 📦 المكتبات المضافة

تم إضافة المكتبات التالية في [build.gradle.kts](app/build.gradle.kts):

```kotlin
// Supabase Dependencies
val supabaseVersion = "2.0.4"
implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:storage-kt")
implementation("io.github.jan-tennert.supabase:realtime-kt")
implementation("io.github.jan-tennert.supabase:functions-kt")

// Ktor Client (Required by Supabase)
val ktorVersion = "2.3.7"
implementation("io.ktor:ktor-client-android:$ktorVersion")
implementation("io.ktor:ktor-client-core:$ktorVersion")
implementation("io.ktor:ktor-client-cio:$ktorVersion")
implementation("io.ktor:ktor-client-content-negotiation:$ktorVersion")
implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

## 🚀 الاستخدام السريع

### 1. التهيئة الأولية

في `Application` أو `Activity`:

```kotlin
import com.example.parentalcontrol.SupabaseManager

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // تهيئة Supabase
        val supabaseManager = SupabaseManager.getInstance()
        supabaseManager.initialize(
            supabaseUrl = "https://your-project-id.supabase.co",
            supabaseAnonKey = "your-anon-key-here"
        )
    }
}
```

### 2. رفع ملف واحد

```kotlin
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MyActivity : AppCompatActivity() {

    private val supabaseManager = SupabaseManager.getInstance()

    fun uploadFile() {
        val file = File(cacheDir, "example.jpg")

        lifecycleScope.launch {
            val result = supabaseManager.uploadFile(
                file = file,
                bucket = "uploads",
                folder = "images"  // اختياري
            )

            when (result) {
                is UploadResult.Success -> {
                    Log.i(TAG, "File uploaded: ${result.publicUrl}")
                    Toast.makeText(
                        this@MyActivity,
                        "✓ تم الرفع بنجاح",
                        Toast.LENGTH_SHORT
                    ).show()
                }

                is UploadResult.Error -> {
                    Log.e(TAG, "Upload failed: ${result.message}")
                    Toast.makeText(
                        this@MyActivity,
                        "✗ فشل الرفع",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }
}
```

### 3. رفع عدة ملفات

```kotlin
fun uploadMultipleFiles() {
    val files = listOf(
        File(cacheDir, "photo1.jpg"),
        File(cacheDir, "photo2.jpg"),
        File(cacheDir, "photo3.jpg")
    )

    lifecycleScope.launch {
        val results = supabaseManager.uploadMultipleFiles(
            files = files,
            bucket = "uploads",
            folder = "batch"
        )

        val successCount = results.count { it is UploadResult.Success }
        Log.i(TAG, "Uploaded $successCount of ${files.size} files")
    }
}
```

### 4. تحميل ملف

```kotlin
fun downloadFile() {
    lifecycleScope.launch {
        val result = supabaseManager.downloadFile(
            bucket = "uploads",
            filePath = "images/example.jpg"
        )

        when (result) {
            is DownloadResult.Success -> {
                // حفظ الملف محلياً
                val localFile = File(cacheDir, result.fileName)
                localFile.writeBytes(result.data)

                Log.i(TAG, "File downloaded: ${localFile.path}")
            }

            is DownloadResult.Error -> {
                Log.e(TAG, "Download failed: ${result.message}")
            }
        }
    }
}
```

### 5. حذف ملف

```kotlin
fun deleteFile() {
    lifecycleScope.launch {
        val deleted = supabaseManager.deleteFile(
            bucket = "uploads",
            filePath = "images/example.jpg"
        )

        if (deleted) {
            Log.i(TAG, "File deleted successfully")
        }
    }
}
```

### 6. عرض قائمة الملفات

```kotlin
fun listFiles() {
    lifecycleScope.launch {
        val files = supabaseManager.listFiles(
            bucket = "uploads",
            folder = "images"
        )

        files.forEach { file ->
            Log.i(TAG, "File: ${file.name} (${file.createdAt})")
        }
    }
}
```

## 📱 مثال كامل: رفع صورة من المعرض

```kotlin
class PhotoUploadActivity : AppCompatActivity() {

    private val supabaseManager = SupabaseManager.getInstance()

    // مشغل لاختيار صورة
    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadImage(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // تهيئة Supabase
        supabaseManager.initialize(
            supabaseUrl = SUPABASE_URL,
            supabaseAnonKey = SUPABASE_ANON_KEY
        )

        // زر اختيار الصورة
        btnSelectImage.setOnClickListener {
            imagePickerLauncher.launch("image/*")
        }
    }

    private fun uploadImage(uri: Uri) {
        lifecycleScope.launch {
            try {
                // نسخ الملف إلى cache
                val file = copyUriToFile(uri)

                // رفع الملف
                showProgress("جاري الرفع...")

                val result = supabaseManager.uploadFile(
                    file = file,
                    bucket = "uploads",
                    folder = "photos"
                )

                hideProgress()

                when (result) {
                    is UploadResult.Success -> {
                        showSuccess(
                            "تم رفع الصورة!\n" +
                            "الرابط: ${result.publicUrl}"
                        )

                        // حذف الملف المؤقت
                        file.delete()
                    }

                    is UploadResult.Error -> {
                        showError("فشل الرفع: ${result.message}")
                    }
                }
            } catch (e: Exception) {
                hideProgress()
                showError("خطأ: ${e.message}")
            }
        }
    }

    private suspend fun copyUriToFile(uri: Uri): File = withContext(Dispatchers.IO) {
        val inputStream = contentResolver.openInputStream(uri)
            ?: throw Exception("Cannot open file")

        val file = File(cacheDir, "upload_${System.currentTimeMillis()}.jpg")

        inputStream.use { input ->
            FileOutputStream(file).use { output ->
                input.copyTo(output)
            }
        }

        file
    }
}
```

## 🔒 أفضل الممارسات الأمنية

### 1. إخفاء المفاتيح السرية

**❌ خطأ:**
```kotlin
const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
```

**✅ صحيح:** استخدم `local.properties` أو `BuildConfig`

```kotlin
// في build.gradle.kts
android {
    defaultConfig {
        buildConfigField("String", "SUPABASE_URL", "\"${project.properties["SUPABASE_URL"]}\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"${project.properties["SUPABASE_ANON_KEY"]}\"")
    }
}

// في الكود
supabaseManager.initialize(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseAnonKey = BuildConfig.SUPABASE_ANON_KEY
)
```

### 2. استخدام Row Level Security (RLS)

في Supabase SQL Editor:

```sql
-- تفعيل RLS
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- السماح فقط برفع ملفات أصغر من 10MB
CREATE POLICY "Limit file size"
ON storage.objects FOR INSERT
TO public
WITH CHECK (
    bucket_id = 'uploads' AND
    (octet_length(decode(metadata->>'size', 'escape')) < 10485760)
);

-- السماح فقط بأنواع ملفات معينة
CREATE POLICY "Allow only images"
ON storage.objects FOR INSERT
TO public
WITH CHECK (
    bucket_id = 'uploads' AND
    (metadata->>'mimetype' SIMILAR TO '(image/jpeg|image/png|image/gif)')
);
```

### 3. التحقق من نوع الملف قبل الرفع

```kotlin
fun isImageFile(file: File): Boolean {
    val mimeType = contentResolver.getType(Uri.fromFile(file))
    return mimeType?.startsWith("image/") == true
}

fun uploadFileWithValidation(file: File) {
    if (!isImageFile(file)) {
        showError("يُسمح فقط برفع الصور")
        return
    }

    if (file.length() > 10 * 1024 * 1024) { // 10MB
        showError("حجم الملف أكبر من 10MB")
        return
    }

    // متابعة الرفع...
}
```

## 📊 معالجة التقدم (Progress Tracking)

للأسف، Supabase Storage لا يدعم progress callbacks مباشرة، ولكن يمكنك إضافة مؤشر تقدم بسيط:

```kotlin
fun uploadWithProgress(file: File) {
    lifecycleScope.launch {
        try {
            // عرض ProgressBar
            progressBar.visibility = View.VISIBLE
            progressBar.isIndeterminate = true

            val result = supabaseManager.uploadFile(file, "uploads")

            progressBar.visibility = View.GONE

            // معالجة النتيجة...
        } catch (e: Exception) {
            progressBar.visibility = View.GONE
            showError(e.message)
        }
    }
}
```

## 🧪 الاختبار

### اختبار الرفع محلياً

```kotlin
@Test
fun testFileUpload() = runBlocking {
    val supabaseManager = SupabaseManager.getInstance()
    supabaseManager.initialize(TEST_URL, TEST_KEY)

    val testFile = createTestFile()
    val result = supabaseManager.uploadFile(
        file = testFile,
        bucket = "test-uploads"
    )

    assertTrue(result is UploadResult.Success)
}

private fun createTestFile(): File {
    val file = File(context.cacheDir, "test.txt")
    file.writeText("Test content")
    return file
}
```

## 🔍 استكشاف الأخطاء

### خطأ: "Failed to initialize Supabase"
**الحل:** تحقق من صحة `SUPABASE_URL` و `SUPABASE_ANON_KEY`

### خطأ: "Bucket not found"
**الحل:** تأكد من إنشاء Bucket في Supabase Dashboard

### خطأ: "Upload failed: 413"
**الحل:** الملف أكبر من الحد المسموح (افتراضياً 50MB)

### خطأ: "Network error"
**الحل:** تأكد من إضافة صلاحية الإنترنت في `AndroidManifest.xml`:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 📚 موارد إضافية

- [Supabase Storage Documentation](https://supabase.com/docs/guides/storage)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Android File Handling Best Practices](https://developer.android.com/training/data-storage)

## 🎯 الملخص

### ملفات المشروع الرئيسية:

1. **[SupabaseManager.kt](app/src/main/java/com/example/parentalcontrol/SupabaseManager.kt)** - الكلاس الرئيسي لإدارة Supabase
2. **[FileUploadExampleActivity.kt](app/src/main/java/com/example/parentalcontrol/FileUploadExampleActivity.kt)** - مثال كامل على الاستخدام
3. **[build.gradle.kts](app/build.gradle.kts)** - المكتبات المطلوبة
4. **[AndroidManifest.xml](app/src/main/AndroidManifest.xml)** - الصلاحيات المطلوبة

### الوظائف الأساسية:

| الوظيفة | الوصف |
|---------|-------|
| `initialize()` | تهيئة Supabase Client |
| `uploadFile()` | رفع ملف واحد |
| `uploadMultipleFiles()` | رفع عدة ملفات |
| `downloadFile()` | تحميل ملف |
| `deleteFile()` | حذف ملف |
| `listFiles()` | عرض قائمة الملفات |
| `getPublicUrl()` | الحصول على رابط عام |

---

**ملاحظة نهائية:** تذكر دائماً استخدام Coroutines (`lifecycleScope.launch`) لضمان عدم تجميد واجهة المستخدم أثناء العمليات طويلة الأمد! 🚀
