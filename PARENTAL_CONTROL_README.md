# Parental Control - Device Admin للرقابة الأبوية

## 📋 نظرة عامة

هذا المشروع يوفر تطبيق Android للرقابة الأبوية باستخدام Device Administration API. يتيح للآباء التحكم في أجهزة أطفالهم وحمايتهم من خلال ميزات متقدمة.

## ⚠️ تحذير مهم

هذا الكود مخصص **فقط** لأغراض الرقابة الأبوية الشرعية والدفاعية. يجب استخدامه بمسؤولية وفقاً للقوانين المحلية وبموافقة المستخدمين.

## 🔑 الميزات الرئيسية

### 1. **Force Lock (القفل الفوري)**
- قفل شاشة الجهاز فوراً
- مفيد لتطبيق وقت النوم أو حد استخدام الجهاز

### 2. **Wipe Data (مسح البيانات)**
- إعادة تعيين الجهاز لإعدادات المصنع
- مفيد في حالة فقدان الجهاز أو الطوارئ
- ⚠️ **خطير**: يمسح كل البيانات نهائياً

### 3. **ميزات إضافية**
- مراقبة محاولات كلمة المرور الفاشلة
- تعيين حد أقصى لمحاولات كلمة المرور
- القفل التلقائي بعد مدة معينة
- تعطيل/تفعيل الكاميرا
- تشفير التخزين

## 📁 بنية الملفات

```
app/src/main/
├── java/com/example/parentalcontrol/
│   ├── MyDeviceAdminReceiver.kt      # مستقبل Device Admin
│   ├── ParentalControlManager.kt     # كلاس مساعد للعمليات
│   └── MainActivity.kt                # مثال على الاستخدام
├── res/xml/
│   └── device_admin_policies.xml     # تعريف السياسات والصلاحيات
└── AndroidManifest_example.xml       # مثال على التكوين
```

## 🚀 خطوات الإعداد

### 1. إضافة الصلاحيات في AndroidManifest.xml

```xml
<uses-permission android:name="android.permission.BIND_DEVICE_ADMIN" />

<receiver
    android:name=".MyDeviceAdminReceiver"
    android:permission="android.permission.BIND_DEVICE_ADMIN"
    android:exported="true">

    <meta-data
        android:name="android.app.device_admin"
        android:resource="@xml/device_admin_policies" />

    <intent-filter>
        <action android:name="android.app.action.DEVICE_ADMIN_ENABLED" />
    </intent-filter>
</receiver>
```

### 2. استخدام ParentalControlManager

```kotlin
// تهيئة المدير
val manager = ParentalControlManager(context)

// التحقق من التفعيل
if (!manager.isAdminActive()) {
    // طلب التفعيل
    manager.requestEnableAdmin(activity)
}

// استخدام الميزات
manager.lockScreen()
manager.setCameraDisabled(true)
manager.setMaximumTimeToLock(30000) // 30 ثانية
```

### 3. معالجة نتيجة التفعيل

```kotlin
override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    if (requestCode == ParentalControlManager.REQUEST_CODE_ENABLE_ADMIN) {
        if (resultCode == Activity.RESULT_OK) {
            // تم التفعيل بنجاح
        }
    }
}
```

## 🔒 السياسات المتاحة في device_admin_policies.xml

| السياسة | الوصف | مستوى الخطورة |
|---------|-------|---------------|
| `force-lock` | قفل الشاشة فوراً | منخفض |
| `wipe-data` | مسح جميع البيانات | **عالي جداً** |
| `watch-login` | مراقبة محاولات تسجيل الدخول | منخفض |
| `reset-password` | إعادة تعيين كلمة المرور | متوسط |
| `limit-password` | فرض متطلبات كلمة المرور | منخفض |
| `expire-password` | انتهاء صلاحية كلمة المرور | منخفض |
| `encrypted-storage` | تشفير التخزين | منخفض |
| `disable-camera` | تعطيل الكاميرا | متوسط |

## 📱 الوظائف المتاحة

### قفل الشاشة
```kotlin
manager.lockScreen()
```

### مسح البيانات (⚠️ خطير!)
```kotlin
// مسح البيانات الداخلية فقط
manager.wipeData(wipeExternalStorage = false)

// مسح البيانات الداخلية والخارجية
manager.wipeData(wipeExternalStorage = true)
```

### تعطيل الكاميرا
```kotlin
manager.setCameraDisabled(true)  // تعطيل
manager.setCameraDisabled(false) // تفعيل

// التحقق من الحالة
val isDisabled = manager.isCameraDisabled()
```

### تعيين حد محاولات كلمة المرور
```kotlin
// بعد 5 محاولات فاشلة، يمكن مسح البيانات تلقائياً
manager.setMaximumFailedPasswordsForWipe(5)
```

### القفل التلقائي
```kotlin
// قفل الشاشة تلقائياً بعد 30 ثانية
manager.setMaximumTimeToLock(30000)
```

## 🔐 اعتبارات الأمان

1. **استخدام مسؤول**: هذه الصلاحيات قوية جداً ويجب استخدامها بحذر
2. **موافقة المستخدم**: احصل على موافقة صريحة قبل تفعيل Device Admin
3. **التحذيرات**: اعرض تحذيرات واضحة قبل العمليات الخطيرة (خاصة wipe-data)
4. **التشفير**: فكر في تشفير الإعدادات الحساسة
5. **المصادقة**: استخدم كلمة مرور للآباء قبل تغيير الإعدادات

## ⚡ قيود مهمة

### Android 8.0+ (API 26+)
- `resetPassword()` لم يعد يعمل لأسباب أمنية
- استخدم `resetPasswordWithToken()` كبديل

### Android 10+ (API 29+)
- قيود إضافية على الوصول للتخزين الخارجي

### Work Profile / Device Owner
- بعض الميزات تتطلب أن يكون التطبيق Device Owner
- Device Owner يتطلب إعداد خاص أثناء التهيئة الأولية للجهاز

## 🧪 الاختبار

```kotlin
// اختبر على جهاز حقيقي، وليس المحاكي
// بعض الميزات قد لا تعمل على المحاكي

class ParentalControlTests {

    @Test
    fun testLockScreen() {
        val manager = ParentalControlManager(context)
        assertTrue(manager.isAdminActive())
        assertTrue(manager.lockScreen())
    }

    @Test
    fun testCameraDisabled() {
        val manager = ParentalControlManager(context)
        manager.setCameraDisabled(true)
        assertTrue(manager.isCameraDisabled())
    }
}
```

## 📖 مصادر إضافية

- [Android Device Administration](https://developer.android.com/guide/topics/admin/device-admin)
- [DevicePolicyManager Documentation](https://developer.android.com/reference/android/app/admin/DevicePolicyManager)
- [Device Owner vs Device Admin](https://developer.android.com/work/dpc/dedicated-devices/device-owner)

## ⚖️ الترخيص والمسؤولية

- هذا الكود للأغراض التعليمية والاستخدام الشرعي فقط
- المطور غير مسؤول عن أي سوء استخدام
- يجب الالتزام بالقوانين المحلية والدولية للخصوصية
- احصل على موافقة صريحة من مستخدمي الأجهزة

## 🐛 المشاكل الشائعة وحلولها

### المشكلة: لا يمكن تفعيل Device Admin
**الحل**: تأكد من إضافة الصلاحيات الصحيحة في AndroidManifest.xml

### المشكلة: lockScreen() لا يعمل
**الحل**: تحقق من أن Device Admin مفعل باستخدام `isAdminActive()`

### المشكلة: wipeData() لا يفعل شيء
**الحل**: تأكد من أن السياسة `<wipe-data />` موجودة في device_admin_policies.xml

### المشكلة: resetPassword() لا يعمل
**الحل**: هذه الميزة متوقفة في Android 8.0+، استخدم بدائل أخرى

## 📞 الدعم

إذا واجهت مشاكل:
1. تحقق من Logcat للأخطاء
2. تأكد من تفعيل Device Admin
3. تحقق من إصدار Android
4. راجع الصلاحيات في Manifest

---

**ملاحظة أخيرة**: استخدم هذا الكود بمسؤولية وأخلاقية. الرقابة الأبوية يجب أن تكون متوازنة مع احترام خصوصية الأطفال ومناسبة لأعمارهم.
