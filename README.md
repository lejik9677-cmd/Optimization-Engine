# Optimization Engine - Android Project

مشروع Android متكامل يحتوي على:
- 📤 **وحدة رفع الملفات** باستخدام Supabase Storage
- 🔐 **نظام Device Admin** لإدارة الجهاز
- 🏗️ **بنية نظيفة** مع أفضل الممارسات في Kotlin

## 🌟 الميزات

### 1. Supabase File Upload Module

وحدة متكاملة لرفع وتحميل الملفات من وإلى Supabase Storage:

- ✅ رفع ملف واحد أو عدة ملفات
- ✅ تحميل الملفات من السحابة
- ✅ حذف الملفات
- ✅ عرض قائمة الملفات
- ✅ الحصول على روابط عامة
- ✅ استخدام Kotlin Coroutines للأداء الأمثل
- ✅ معالجة الأخطاء بشكل احترافي

### 2. Device Admin Receiver

نظام كامل لإدارة الجهاز (للأغراض الدفاعية والرقابة الأبوية):

- 🔒 قفل الشاشة فوراً
- 🗑️ مسح البيانات (Factory Reset)
- 📷 تعطيل/تفعيل الكاميرا
- 🔑 إدارة كلمات المرور
- ⏰ القفل التلقائي

## 📁 هيكل المشروع

```
Optimization-Engine/
├── app/
│   ├── build.gradle.kts          # تكوين التطبيق + المكتبات
│   └── src/main/
│       ├── java/com/example/parentalcontrol/
│       │   ├── SupabaseManager.kt              # 📤 مدير Supabase
│       │   ├── FileUploadExampleActivity.kt    # مثال كامل للرفع
│       │   ├── MyDeviceAdminReceiver.kt        # 🔐 Device Admin
│       │   ├── ParentalControlManager.kt       # مدير الرقابة
│       │   └── MainActivity.kt                 # النشاط الرئيسي
│       ├── res/xml/
│       │   ├── device_admin_policies.xml       # صلاحيات Device Admin
│       │   └── file_paths.xml                  # FileProvider config
│       └── AndroidManifest.xml                 # التكوين الرئيسي
├── build.gradle.kts              # تكوين المشروع
├── settings.gradle.kts           # إعدادات Gradle
├── gradle.properties             # خصائص Gradle
├── .gitignore                    # استبعاد الملفات
├── local.properties.example      # مثال للإعدادات المحلية
├── README.md                     # هذا الملف
├── QUICK_START.md                # 🚀 دليل البدء السريع
├── SUPABASE_FILE_UPLOAD_GUIDE.md # 📚 دليل شامل للرفع
└── PARENTAL_CONTROL_README.md    # 🔐 دليل Device Admin
```

## 🚀 البدء السريع

### 1. متطلبات التشغيل

- Android Studio Hedgehog (2023.1.1) أو أحدث
- JDK 17+
- Android SDK 24+ (Android 7.0+)
- حساب [Supabase](https://supabase.com) (مجاني)

### 2. الإعداد

```bash
# 1. نسخ المشروع
git clone https://github.com/your-username/Optimization-Engine.git
cd Optimization-Engine

# 2. إعداد local.properties
cp local.properties.example local.properties

# 3. تعديل local.properties بمعلومات Supabase الخاصة بك
# SUPABASE_URL=https://xxxxx.supabase.co
# SUPABASE_ANON_KEY=your-anon-key

# 4. فتح المشروع في Android Studio
# 5. انتظر Gradle Sync
# 6. شغّل التطبيق
```

### 3. مثال سريع

```kotlin
// تهيئة Supabase
val supabaseManager = SupabaseManager.getInstance()
supabaseManager.initialize(
    supabaseUrl = "https://xxxxx.supabase.co",
    supabaseAnonKey = "your-key"
)

// رفع ملف
lifecycleScope.launch {
    val result = supabaseManager.uploadFile(
        file = File("/path/to/file.jpg"),
        bucket = "uploads"
    )

    when (result) {
        is UploadResult.Success -> println("✓ ${result.publicUrl}")
        is UploadResult.Error -> println("✗ ${result.message}")
    }
}
```

## 📚 التوثيق

| الملف | الوصف |
|-------|-------|
| [QUICK_START.md](QUICK_START.md) | دليل البدء السريع في 5 دقائق |
| [SUPABASE_FILE_UPLOAD_GUIDE.md](SUPABASE_FILE_UPLOAD_GUIDE.md) | دليل شامل لوحدة رفع الملفات |
| [PARENTAL_CONTROL_README.md](PARENTAL_CONTROL_README.md) | دليل Device Admin والرقابة الأبوية |

## 🔑 الملفات الرئيسية

### للتعامل مع Supabase:

- **[SupabaseManager.kt](app/src/main/java/com/example/parentalcontrol/SupabaseManager.kt)** - كلاس Singleton لإدارة جميع عمليات Supabase
- **[FileUploadExampleActivity.kt](app/src/main/java/com/example/parentalcontrol/FileUploadExampleActivity.kt)** - أمثلة عملية كاملة

### للتعامل مع Device Admin:

- **[MyDeviceAdminReceiver.kt](app/src/main/java/com/example/parentalcontrol/MyDeviceAdminReceiver.kt)** - مستقبل أحداث Device Admin
- **[ParentalControlManager.kt](app/src/main/java/com/example/parentalcontrol/ParentalControlManager.kt)** - واجهة سهلة للتحكم

## 🛠️ التقنيات المستخدمة

- **Kotlin** 1.9.20 - لغة البرمجة
- **Supabase** 2.0.4 - Backend as a Service
- **Ktor** 2.3.7 - HTTP Client
- **Coroutines** 1.7.3 - البرمجة غير المتزامنة
- **AndroidX** - مكتبات Android الحديثة
- **Material Design** - تصميم المواد

## 📦 المكتبات الرئيسية

```kotlin
// Supabase
implementation("io.github.jan-tennert.supabase:postgrest-kt")
implementation("io.github.jan-tennert.supabase:storage-kt")

// Ktor
implementation("io.ktor:ktor-client-android:2.3.7")
implementation("io.ktor:ktor-client-content-negotiation:2.3.7")

// Coroutines
implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
```

## 🔒 الأمان

⚠️ **مهم جداً:**

1. **لا ترفع `local.properties`** إلى Git (مضاف في `.gitignore`)
2. **استخدم BuildConfig** للمفاتيح في الإنتاج
3. **فعّل Row Level Security** في Supabase
4. **راجع صلاحيات الـ Bucket** بانتظام

### مثال: استخدام BuildConfig (للإنتاج)

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

## 🧪 الاختبار

```kotlin
// مثال على اختبار بسيط
@Test
fun testFileUpload() = runBlocking {
    val manager = SupabaseManager.getInstance()
    manager.initialize(TEST_URL, TEST_KEY)

    val testFile = File("test.txt").apply {
        writeText("Test content")
    }

    val result = manager.uploadFile(testFile, "test-bucket")
    assertTrue(result is UploadResult.Success)
}
```

## 🐛 المشاكل الشائعة

| المشكلة | الحل |
|---------|------|
| "Bucket not found" | أنشئ bucket في Supabase Dashboard |
| "Permission denied" | أضف RLS policies في Supabase |
| "Network error" | تحقق من صلاحية INTERNET في Manifest |
| "Failed to initialize" | تحقق من صحة URL و Key |

## 📊 الأداء

- ✅ رفع الملفات في **Background Thread** (Coroutines)
- ✅ لا توجد عمليات حظر على **Main Thread**
- ✅ استخدام **lifecycleScope** للإلغاء التلقائي
- ✅ معالجة الأخطاء بشكل احترافي

## 🌐 الموارد

- [Supabase Documentation](https://supabase.com/docs)
- [Kotlin Coroutines Guide](https://kotlinlang.org/docs/coroutines-guide.html)
- [Android Developers Guide](https://developer.android.com)
- [Ktor Documentation](https://ktor.io/docs/client.html)

## 📄 الترخيص

هذا المشروع للأغراض التعليمية والاستخدام الشرعي فقط.

## 🤝 المساهمة

المساهمات مرحب بها! الرجاء:

1. Fork المشروع
2. أنشئ branch للميزة (`git checkout -b feature/AmazingFeature`)
3. Commit التغييرات (`git commit -m 'Add some AmazingFeature'`)
4. Push للـ branch (`git push origin feature/AmazingFeature`)
5. افتح Pull Request

## 📞 الدعم

إذا واجهت مشاكل:

1. راجع [QUICK_START.md](QUICK_START.md)
2. تحقق من [استكشاف الأخطاء](SUPABASE_FILE_UPLOAD_GUIDE.md#-استكشاف-الأخطاء)
3. افتح [Issue جديد](https://github.com/your-username/Optimization-Engine/issues)

## ⭐ إذا أعجبك المشروع

إذا وجدت هذا المشروع مفيداً، لا تنسَ إعطائه نجمة ⭐ على GitHub!

---

**صُنع بـ ❤️ باستخدام Kotlin و Supabase**
