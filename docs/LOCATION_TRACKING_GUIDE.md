# 📍 دليل تتبع الموقع الشفاف والأخلاقي

## ⚠️ إخلاء المسؤولية المهم

هذا الدليل يوضح كيفية إنشاء **نظام تتبع موقع شفاف وأخلاقي** للرقابة الأبوية.

### ✅ الاستخدام الشرعي:
- الحصول على **موافقة صريحة** من المستخدم
- **إشعارات واضحة** عند التتبع النشط
- **شفافية كاملة** حول البيانات المجمعة
- **حق الإلغاء** في أي وقت

### ❌ ممنوع:
- التتبع الخفي بدون علم المستخدم
- جمع البيانات بدون موافقة
- إخفاء الغرض الحقيقي للتتبع

---

## 🌟 المبادئ الأخلاقية المطبقة

### 1. **الموافقة المستنيرة (Informed Consent)**
```kotlin
// ✅ صحيح: شرح واضح قبل الموافقة
fun showConsentDialog() {
    val message = """
        📍 نحتاج موافقتك لتتبع موقعك للأسباب التالية:

        ✓ الأمان الشخصي
        ✓ معرفة مكانك في حالات الطوارئ

        البيانات المجمعة:
        • الموقع GPS
        • الوقت والتاريخ
        • دقة الموقع

        حقوقك:
        • يمكنك إيقاف التتبع في أي وقت
        • يمكنك حذف بياناتك
        • سيظهر إشعار عند التتبع
    """

    showDialog(message)
}

// ❌ خطأ: موافقة مخفية أو مضللة
```

### 2. **الشفافية الدائمة (Ongoing Transparency)**
```kotlin
// ✅ إشعار دائم في شريط الإشعارات
private fun showTrackingNotification() {
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setContentTitle("🌍 Location Tracking Active")
        .setContentText("Your location is being shared")
        .setOngoing(true) // لا يمكن رفضه
        .build()
}

// ✅ المستخدم يرى دائماً أن التتبع نشط
```

### 3. **حق الإلغاء (Right to Withdraw)**
```kotlin
// ✅ السماح بإيقاف التتبع في أي وقت
fun stopTracking() {
    locationTracker.stopTracking()
    locationTracker.setUserConsent(false)
}

// ✅ السماح بحذف جميع البيانات
suspend fun deleteAllData() {
    locationTracker.deleteAllLocationData()
}
```

### 4. **التناسب (Proportionality)**
```kotlin
// ✅ جمع البيانات الضرورية فقط
data class LocationData(
    val latitude: Double,       // ضروري
    val longitude: Double,      // ضروري
    val timestamp: String,      // ضروري
    val accuracy: Float,        // مفيد
    val batteryLevel: Int?      // اختياري فقط
)

// ❌ خطأ: جمع بيانات غير ضرورية
// مثل: قائمة جهات الاتصال، المكالمات، الرسائل
```

### 5. **الحد الأدنى من التدخل (Minimal Intrusion)**
```kotlin
// ✅ تحديث كل 10 دقائق (معقول)
const val UPDATE_INTERVAL_MS = 10 * 60 * 1000L

// ❌ خطأ: تحديث كل ثانية (تطفلي)
const val UPDATE_INTERVAL_MS = 1000L
```

---

## 📦 الملفات المنشأة

| الملف | الوصف |
|-------|-------|
| **[LocationData.kt](app/src/main/java/com/example/parentalcontrol/LocationData.kt)** | نموذج بيانات الموقع |
| **[LocationTrackerManager.kt](app/src/main/java/com/example/parentalcontrol/LocationTrackerManager.kt)** | ✅ المدير الرئيسي مع الشفافية |
| **[LocationConsentActivity.kt](app/src/main/java/com/example/parentalcontrol/LocationConsentActivity.kt)** | ✅ واجهة الموافقة الأخلاقية |
| **[SupabaseManager.kt](app/src/main/java/com/example/parentalcontrol/SupabaseManager.kt)** | تم إضافة وظائف حفظ الموقع |

---

## 🚀 الإعداد السريع

### الخطوة 1: إنشاء جدول في Supabase

في Supabase SQL Editor، نفذ:

```sql
-- إنشاء جدول locations
CREATE TABLE locations (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    accuracy REAL NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    device_id TEXT NOT NULL,
    battery_level INTEGER,
    is_charging BOOLEAN,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- إنشاء فهرس لتسريع الاستعلامات
CREATE INDEX idx_locations_device_id ON locations(device_id);
CREATE INDEX idx_locations_timestamp ON locations(timestamp);

-- تفعيل Row Level Security (RLS)
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;

-- سياسة للسماح بالإدراج (فقط للمستخدمين المصرح لهم)
CREATE POLICY "Allow authenticated inserts"
ON locations FOR INSERT
TO authenticated
WITH CHECK (true);

-- سياسة للقراءة (فقط بيانات الجهاز نفسه)
CREATE POLICY "Users can view own device locations"
ON locations FOR SELECT
TO authenticated
USING (device_id = current_setting('app.device_id', true));

-- سياسة للحذف (فقط بيانات الجهاز نفسه)
CREATE POLICY "Users can delete own device locations"
ON locations FOR DELETE
TO authenticated
USING (device_id = current_setting('app.device_id', true));
```

### الخطوة 2: تهيئة Supabase في التطبيق

```kotlin
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // تهيئة Supabase
        SupabaseManager.getInstance().initialize(
            supabaseUrl = "https://xxxxx.supabase.co",
            supabaseAnonKey = "your-anon-key"
        )
    }
}
```

### الخطوة 3: الحصول على موافقة المستخدم

```kotlin
class MainActivity : AppCompatActivity() {

    private lateinit var locationTracker: LocationTrackerManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        locationTracker = LocationTrackerManager(this)

        // التحقق من الموافقة
        if (!locationTracker.hasUserConsent()) {
            // عرض نافذة الموافقة
            startActivity(Intent(this, LocationConsentActivity::class.java))
        }
    }
}
```

### الخطوة 4: بدء التتبع

```kotlin
lifecycleScope.launch {
    if (locationTracker.hasUserConsent() &&
        locationTracker.hasLocationPermissions()) {

        val started = locationTracker.startTracking()

        if (started) {
            Log.i(TAG, "Location tracking started")
            // سيظهر إشعار دائم للمستخدم
        }
    }
}
```

---

## 🔐 الصلاحيات المطلوبة

### في AndroidManifest.xml

```xml
<!-- صلاحيات الموقع الأساسية -->
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />

<!-- صلاحية الموقع في الخلفية (Android 10+) -->
<!-- ⚠️ تتطلب تبرير واضح في Google Play Console -->
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />

<!-- صلاحيات الخدمة والإشعارات -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### طلب الصلاحيات بالترتيب الصحيح

```kotlin
// 1. أولاً: اطلب صلاحيات الموقع الأساسية
val permissions = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION
)
requestPermissions(permissions, REQUEST_CODE)

// 2. ثانياً: بعد الموافقة، اطلب صلاحية الخلفية (Android 10+)
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
    requestPermissions(
        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
        REQUEST_CODE_BACKGROUND
    )
}
```

---

## 📊 البيانات المجمعة

### ما يتم جمعه:

| البيان | نوعه | ضروري؟ | السبب |
|--------|------|---------|-------|
| Latitude | Double | ✅ نعم | تحديد الموقع |
| Longitude | Double | ✅ نعم | تحديد الموقع |
| Accuracy | Float | ✅ نعم | معرفة دقة الموقع |
| Timestamp | String | ✅ نعم | معرفة وقت التسجيل |
| Device ID | String | ✅ نعم | ربط البيانات بالجهاز |
| Battery Level | Int | ⚪ لا | معلومة إضافية |
| Is Charging | Boolean | ⚪ لا | معلومة إضافية |

### ما لا يتم جمعه:

❌ جهات الاتصال
❌ المكالمات
❌ الرسائل
❌ الصور
❌ كلمات المرور
❌ بيانات التطبيقات الأخرى

---

## 🎯 الاستخدام الكامل

### مثال: تفعيل التتبع مع الموافقة

```kotlin
class LocationActivity : AppCompatActivity() {

    private val locationTracker = LocationTrackerManager(this)

    fun enableLocationTracking() {
        // 1. التحقق من الموافقة
        if (!locationTracker.hasUserConsent()) {
            showConsentDialog()
            return
        }

        // 2. التحقق من الصلاحيات
        if (!locationTracker.hasLocationPermissions()) {
            requestPermissions()
            return
        }

        // 3. بدء التتبع
        lifecycleScope.launch {
            val started = locationTracker.startTracking()

            if (started) {
                Toast.makeText(
                    this@LocationActivity,
                    "✓ بدأ تتبع الموقع",
                    Toast.LENGTH_SHORT
                ).show()

                // الآن سيظهر إشعار دائم
                // سيتم إرسال الموقع كل 10 دقائق
            }
        }
    }

    private fun showConsentDialog() {
        val message = """
            نحتاج موافقتك لتتبع موقعك.

            الهدف: الأمان الشخصي
            التحديث: كل 10 دقائق
            الإشعار: سيظهر عند التتبع النشط

            حقوقك:
            • إيقاف التتبع في أي وقت
            • حذف جميع البيانات
            • إلغاء الموافقة
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("موافقة على تتبع الموقع")
            .setMessage(message)
            .setPositiveButton("أوافق") { _, _ ->
                locationTracker.setUserConsent(true)
                enableLocationTracking()
            }
            .setNegativeButton("لا أوافق", null)
            .show()
    }
}
```

### مثال: الحصول على الموقع الحالي (لمرة واحدة)

```kotlin
fun getCurrentLocation() {
    lifecycleScope.launch {
        val result = locationTracker.getCurrentLocation()

        when (result) {
            is LocationResult.Success -> {
                Log.i(TAG, "Location: ${result.latitude}, ${result.longitude}")
                Log.i(TAG, "Accuracy: ${result.accuracy} meters")
            }
            is LocationResult.Error -> {
                Log.e(TAG, "Error: ${result.message}")
            }
        }
    }
}
```

### مثال: إيقاف التتبع

```kotlin
fun stopTracking() {
    lifecycleScope.launch {
        val stopped = locationTracker.stopTracking()

        if (stopped) {
            Toast.makeText(this, "✓ توقف التتبع", Toast.LENGTH_SHORT).show()
            // الإشعار سيختفي تلقائياً
        }
    }
}
```

### مثال: حذف جميع البيانات

```kotlin
fun deleteAllData() {
    AlertDialog.Builder(this)
        .setTitle("⚠️ حذف البيانات")
        .setMessage("هل تريد حذف جميع بيانات الموقع؟")
        .setPositiveButton("نعم") { _, _ ->
            lifecycleScope.launch {
                val deleted = locationTracker.deleteAllLocationData()

                if (deleted) {
                    Toast.makeText(
                        this@MainActivity,
                        "✓ تم حذف جميع البيانات",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
        .setNegativeButton("إلغاء", null)
        .show()
}
```

---

## ⚖️ الامتثال القانوني

### GDPR (الاتحاد الأوروبي)

✅ **متطلبات GDPR المطبقة:**
- ✅ موافقة صريحة قبل الجمع
- ✅ شفافية حول البيانات المجمعة
- ✅ حق الوصول للبيانات
- ✅ حق الحذف (Right to be forgotten)
- ✅ حق إلغاء الموافقة
- ✅ الحد الأدنى من البيانات

### COPPA (الولايات المتحدة - الأطفال تحت 13)

✅ **متطلبات COPPA:**
- ✅ موافقة الوالدين (إذا كان للأطفال)
- ✅ إشعار واضح بالبيانات المجمعة
- ✅ أمان البيانات
- ✅ السماح للوالدين بمراجعة البيانات
- ✅ السماح بحذف البيانات

### قوانين الخصوصية العربية

معظم الدول العربية لديها قوانين مشابهة:
- ✅ الموافقة المستنيرة
- ✅ الشفافية
- ✅ حماية البيانات الشخصية
- ✅ حق الوصول والحذف

---

## 🛡️ أفضل الممارسات الأمنية

### 1. **تشفير البيانات في النقل**

```kotlin
// ✅ Supabase يستخدم HTTPS تلقائياً
// جميع البيانات مشفرة أثناء الإرسال
```

### 2. **Row Level Security (RLS)**

```sql
-- في Supabase، فعّل RLS
ALTER TABLE locations ENABLE ROW LEVEL SECURITY;

-- كل جهاز يرى بياناته فقط
CREATE POLICY "Device can view own data"
ON locations FOR SELECT
USING (device_id = current_setting('app.device_id', true));
```

### 3. **الحد من الاحتفاظ بالبيانات**

```sql
-- حذف المواقع القديمة (أكثر من 30 يوم)
DELETE FROM locations
WHERE created_at < NOW() - INTERVAL '30 days';

-- أو استخدم Supabase Cron Job لجدولة الحذف
```

### 4. **تدقيق الوصول (Audit Logging)**

```sql
-- تسجيل من يصل للبيانات
CREATE TABLE location_access_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    device_id TEXT NOT NULL,
    accessed_by TEXT NOT NULL,
    accessed_at TIMESTAMPTZ DEFAULT NOW()
);
```

---

## 🚨 تحذيرات مهمة

### ⚠️ لا تفعل هذا:

❌ **التتبع الخفي**
```kotlin
// ❌ خطأ: لا إشعار، لا موافقة
locationTracker.startTracking() // دون علم المستخدم
```

❌ **التحديث المتكرر المفرط**
```kotlin
// ❌ خطأ: كل ثانية (استنزاف البطارية + تطفلي)
const val UPDATE_INTERVAL_MS = 1000L
```

❌ **جمع بيانات إضافية غير ضرورية**
```kotlin
// ❌ خطأ: بيانات غير ضرورية
data class LocationData(
    val contacts: List<Contact>,    // ❌ ليس ضروري
    val messages: List<Message>,    // ❌ ليس ضروري
    val apps: List<String>          // ❌ ليس ضروري
)
```

### ✅ افعل هذا:

✅ **الشفافية الكاملة**
```kotlin
// ✅ صحيح: إشعار دائم
showNotification("Location tracking active")
```

✅ **التحديث المعقول**
```kotlin
// ✅ صحيح: كل 10 دقائق
const val UPDATE_INTERVAL_MS = 10 * 60 * 1000L
```

✅ **البيانات الضرورية فقط**
```kotlin
// ✅ صحيح: فقط البيانات المطلوبة
data class LocationData(
    val latitude: Double,
    val longitude: Double,
    val timestamp: String
)
```

---

## 📱 متطلبات Google Play Store

عند نشر التطبيق على Google Play، يجب:

### 1. **تبرير صلاحية الموقع في الخلفية**

في Google Play Console → App Content → Location permissions:

```
We use background location to:
- Ensure child safety by allowing parents to know their location
- Provide emergency assistance if needed
- The app shows a persistent notification when tracking is active
- Users can disable tracking at any time
```

### 2. **سياسة الخصوصية**

يجب أن تتضمن:
- ✅ البيانات التي يتم جمعها
- ✅ كيف يتم استخدامها
- ✅ من يمكنه الوصول إليها
- ✅ كيف يتم حمايتها
- ✅ حقوق المستخدم (الوصول، الحذف، الإلغاء)

### 3. **فيديو توضيحي**

قد يطلب Google Play فيديو يوضح:
- كيف يطلب التطبيق الموافقة
- الإشعار الظاهر عند التتبع النشط
- كيف يمكن للمستخدم إيقاف التتبع

---

## 🔍 استكشاف الأخطاء

### المشكلة: لا يعمل التتبع

**الحل:**
```kotlin
// تحقق من:
1. هل تم منح الموافقة؟
   locationTracker.hasUserConsent()

2. هل تم منح الصلاحيات؟
   locationTracker.hasLocationPermissions()
   locationTracker.hasBackgroundLocationPermission()

3. هل خدمات الموقع مفعلة في الجهاز؟

4. تحقق من Logcat:
   adb logcat -s LocationTracker
```

### المشكلة: لا يتم حفظ البيانات في Supabase

**الحل:**
```kotlin
// تحقق من:
1. هل تم تهيئة Supabase؟
   SupabaseManager.getInstance().initialize(...)

2. هل تم إنشاء جدول locations؟
   تحقق من Supabase Dashboard

3. هل RLS Policies صحيحة؟
   تحقق من السياسات في Supabase

4. تحقق من Logs:
   adb logcat -s SupabaseManager
```

### المشكلة: استنزاف البطارية

**الحل:**
```kotlin
// قلل معدل التحديث
const val UPDATE_INTERVAL_MS = 15 * 60 * 1000L // 15 دقيقة

// استخدم PRIORITY_BALANCED_POWER_ACCURACY
Priority.PRIORITY_BALANCED_POWER_ACCURACY

// أوقف التتبع عند الشحن الكامل (اختياري)
```

---

## 📚 الخلاصة

تم إنشاء نظام تتبع موقع **شفاف وأخلاقي** يحترم:

✅ **الموافقة** - لا تتبع بدون موافقة صريحة
✅ **الشفافية** - إشعار دائم عند التتبع النشط
✅ **الحقوق** - إيقاف وحذف في أي وقت
✅ **التناسب** - بيانات ضرورية فقط
✅ **القانون** - متوافق مع GDPR/COPPA

---

**ملاحظة نهائية:** الرقابة الأبوية يجب أن تكون **شفافة ومتوازنة**. التتبع الخفي يدمر الثقة ويُعتبر تجسساً، حتى لو كان الهدف حسناً.

استخدم هذا النظام **بمسؤولية وأخلاق**.
