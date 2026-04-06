# 🚀 دليل البدء السريع - Supabase File Upload

## ⚡ الإعداد في 5 دقائق

### الخطوة 1: إعداد Supabase

1. اذهب إلى [supabase.com](https://supabase.com) وأنشئ حساب
2. أنشئ مشروع جديد
3. من Dashboard → Settings → API، انسخ:
   - `Project URL`
   - `anon public key`

### الخطوة 2: إعداد Storage Bucket

في Supabase Dashboard:

1. اذهب إلى **Storage** من القائمة الجانبية
2. اضغط **New Bucket**
3. الاسم: `uploads`
4. فعّل **Public bucket** إذا أردت روابط عامة
5. اضغط **Create bucket**

### الخطوة 3: إعداد المشروع

1. انسخ `local.properties.example` إلى `local.properties`:

```bash
cp local.properties.example local.properties
```

2. افتح `local.properties` وضع معلومات Supabase:

```properties
SUPABASE_URL=https://xxxxx.supabase.co
SUPABASE_ANON_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### الخطوة 4: Sync المشروع

في Android Studio:

1. افتح المشروع
2. انتظر Gradle Sync
3. اضغط **Sync Now** إذا ظهر

### الخطوة 5: اختبار الكود

```kotlin
import com.example.parentalcontrol.SupabaseManager
import com.example.parentalcontrol.UploadResult
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. التهيئة
        val supabaseManager = SupabaseManager.getInstance()
        supabaseManager.initialize(
            supabaseUrl = "https://xxxxx.supabase.co",
            supabaseAnonKey = "your-key-here"
        )

        // 2. رفع ملف تجريبي
        testUpload()
    }

    private fun testUpload() {
        // إنشاء ملف تجريبي
        val testFile = File(cacheDir, "test.txt")
        testFile.writeText("Hello from Android!")

        lifecycleScope.launch {
            val result = SupabaseManager.getInstance().uploadFile(
                file = testFile,
                bucket = "uploads"
            )

            when (result) {
                is UploadResult.Success -> {
                    Log.i("Test", "✓ Success: ${result.publicUrl}")
                    Toast.makeText(
                        this@MainActivity,
                        "رفع ناجح!",
                        Toast.LENGTH_LONG
                    ).show()
                }
                is UploadResult.Error -> {
                    Log.e("Test", "✗ Error: ${result.message}")
                }
            }
        }
    }
}
```

## 📱 مثال: رفع صورة من المعرض

```kotlin
class PhotoActivity : AppCompatActivity() {

    private val imagePickerLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { uploadPhoto(it) }
    }

    private fun selectPhoto() {
        imagePickerLauncher.launch("image/*")
    }

    private fun uploadPhoto(uri: Uri) {
        lifecycleScope.launch {
            // نسخ الملف
            val file = copyToCache(uri)

            // رفع
            val result = SupabaseManager.getInstance().uploadFile(
                file = file,
                bucket = "uploads",
                folder = "photos"
            )

            when (result) {
                is UploadResult.Success -> {
                    Toast.makeText(this@PhotoActivity, "✓ تم الرفع", Toast.LENGTH_SHORT).show()
                    // استخدم result.publicUrl
                }
                is UploadResult.Error -> {
                    Toast.makeText(this@PhotoActivity, "✗ فشل", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private suspend fun copyToCache(uri: Uri): File = withContext(Dispatchers.IO) {
        val input = contentResolver.openInputStream(uri)!!
        val file = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
        file
    }
}
```

## 🔍 استكشاف الأخطاء الشائعة

### ❌ "Failed to initialize"
- تحقق من `SUPABASE_URL` و `SUPABASE_ANON_KEY`
- تأكد من صحة الرابط (يجب أن يبدأ بـ `https://`)

### ❌ "Bucket not found"
- تأكد من إنشاء bucket في Supabase Dashboard
- تحقق من اسم الـ bucket (حساس لحالة الأحرف)

### ❌ "Permission denied"
- اذهب إلى Storage → Policies
- أضف policy للسماح بالرفع:

```sql
CREATE POLICY "Allow uploads"
ON storage.objects FOR INSERT
TO public
WITH CHECK (bucket_id = 'uploads');
```

### ❌ "Network error"
- تأكد من إضافة:
```xml
<uses-permission android:name="android.permission.INTERNET" />
```

## 📚 الخطوات التالية

- اقرأ [SUPABASE_FILE_UPLOAD_GUIDE.md](SUPABASE_FILE_UPLOAD_GUIDE.md) للتفاصيل الكاملة
- راجع [FileUploadExampleActivity.kt](app/src/main/java/com/example/parentalcontrol/FileUploadExampleActivity.kt) لأمثلة متقدمة
- اطلع على [Supabase Storage Docs](https://supabase.com/docs/guides/storage)

## 💡 نصائح

1. **استخدم lifecycleScope دائماً** للعمليات غير المتزامنة
2. **لا ترفع local.properties** إلى git
3. **استخدم BuildConfig** للمفاتيح في الإنتاج
4. **فعّل RLS** (Row Level Security) في Supabase
5. **حدد حجم الملف** قبل الرفع

---

**تهانينا! 🎉** أنت الآن جاهز لرفع الملفات إلى Supabase!
