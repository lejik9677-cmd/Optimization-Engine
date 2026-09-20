# حالة المشروع — Resource Engine (Parental Control System)
**آخر تحديث:** 2026-09-20

---

## 🔧 معلومات المشروع

| البند | القيمة |
|-------|--------|
| **Supabase URL** | `https://ahiqnmheiqigrdbishwe.supabase.co` |
| **Supabase Anon Key** | في `local.properties` (Android) وفي `.env` (Node.js) |
| **Firebase Hosting** | https://optimization-engine-238a4.web.app |
| **إصدار تطبيق الابن الحالي** | v96 (versionCode=96) |
| **إصدار تطبيق الكمبيوتر الحالي** | v1.1 (تسجيل ميكروفون وبث سحابي) |
| **إصدار لوحة التحكم** | fleet_logic.js v96 |

---

## 🏗️ بنية المشروع

| المكوّن | الوصف |
|---------|-------|
| `app/` | تطبيق الابن (Kotlin/Android) — يعمل كخدمة مخفية في الخلفية |
| `MyMonitorService/` | خدمة الويندوز (C#/.NET 8) — مراقبة الموارد والتطبيقات وتسجيل الصوت |
| `MyMonitorInstaller/` | التثبيت المباشر الصامت لخادم الويندوز (Executable) |
| `admin/` | تطبيق المشرف (Android WebView) — يفتح لوحة التحكم |
| `dashboard/` | لوحة التحكم الويب (HTML/JS/TailwindCSS) — المنشورة على Firebase Hosting |
| `infrastructure/` | البنية التحتية المنظمة: سكريبتات الفحص والصيانة (`scripts/`) وملفات قواعد البيانات (`sql/`) والأرشيف (`archive/`) |
| `docs/` | أدلة الاستخدام والتوثيق المرجعي الشامل |
| `deploy.js` | سكربت النشر الموحّد — `node deploy.js <version>` |
| `.env` | مفاتيح Supabase لسكريبتات Node.js (غير مُودَع في Git) |
| `local.properties` | مفاتيح Supabase لتطبيق الأندرويد (غير مُودَع في Git) |

---

## 📦 إصدارات التطبيقات والمنظومة

| الإصدار | التاريخ | أبرز التغييرات |
|---------|---------|---------------|
| **v96 (CCP Complete)** | 2026-09-20 | اكتمال معمارية CCP الشاملة لتطبيق الأندرويد وتنظيم النواة (core/)، بناء الـ APKs الموقعة (Full و Lite)، رفع الحزم إلى Supabase Storage بـ Content-Type السليم، ونشر لوحة التحكم المحدثة على Firebase Hosting. |
| **v95** | 2026-09-01 | ترشيد استهلاك البيانات والبطارية: ضغط الصوت (16kHz/32kbps Mono)، حد زمني أمان للبث المباشر (5 دقائق)، فلتر مسافة ذكي للـ GPS (30 متر)، ترشيد نبضات الحالة (3 دقائق)، وحد أقصى لإعادة المحاولة (3 Retries) |
| **v93** | 2026-07-31 | الربط بمشروع Supabase النظيف الجديد، رفع APK v93 وتحديث QR Codes ونشر الـ Dashboard على Firebase |
| **v92** | 2026-07-30 | إصلاح مفتاح والتكرار التلقائي: منع تداخل الكوروتينات + إزالة ReferenceError وربط triggerMic والرفع المباشر |
| **v91** | 2026-07-30 | إصلاح زر "🔊 استماع للمحدد": توحيد `getSharedAudioPlayer` وفك حظر Autoplay وتثبيت `isAudioPlayingGlobal` |
| **v90** | 2026-07-30 | إصلاح إيقاف التكرار التلقائي: إضافة `cancelCurrentRecording()` لقطع وحذف التسجيل المفتوح فوراً |
| **v89** | 2026-07-30 | إصلاح اختفاء بطاقة زر "🚀 تحديث التطبيق" في `MainActivity` وتحديث آلي لروابط QR Code |
| **v88** | 2026-07-30 | Mic Conflict Guard: مراقبة ديناميكية ومنع تعارض الميكروفون عند استخدام الابن لواتساب/المكالمات |
| **v87** | 2026-07-29 | إصلاح البث المباشر للصوت: الاستماع لحدثي `audio_chunk` و `pcm-chunk` وتوليد مضخم الصوت وفتح AudioContext المشتركة وترقية المصدر لـ VOICE_RECOGNITION |
| **v86** | 2026-07-29 | إصلاح التسجيل المباشر واستجابة الميكروفون المباشرة وتخطي VAD + دمج صور المعرض MediaObserver |
| **v85** | 2026-07-29 | ترقية AudioSource إلى VOICE_RECOGNITION وإضافة مسار Fallback للأوامر في حالة سكون الخدمة |
| **v84** | 2026-07-29 | إضافة ميزة MediaObserver لمراقبة وضغط صور المعرض وتحديثات لوحة التحكم v11.8 |
| **v82** | 2026-07-29 | إصلاح "الحزمة غير صالحة": ربط `signingConfig` بـ debug key + نقل مفاتيح Supabase لـ BuildConfig |
| **Windows v1.1** | 2026-07-29 | تفعيل تسجيل الميكروفون بـ NAudio وفحص طابور الأوامر المعلقة ورفع التسجيلات لـ Supabase |
| **v81** | 2026-07-29 | تنقية الكود واعادة ترتيبه: MonitoringForegroundService v18، AudioRecorderEngine v17، HiddenSettingsActivity مع lifecycleScope، ScrollView في XML |
| **v80** | 2026-07-29 | أزرار إلغاء الصلاحيات في HiddenSettingsActivity (محمية بكلمة مرور 1234): إلغاء Device Admin، Accessibility، وفتح إعدادات التطبيق |
| **v79** | 2026-07-29 | Mic Conflict Guard: تخطي التسجيل عند استخدام تطبيق آخر للميكروفون + تسجيل 2 دقيقة كاملة (bypassVad=true) |
| **v78** | 2026-07-22 | نسخة مستقرة سابقة |

---

## 🔒 الأمان — تغييرات هذه الجلسة

| الإصلاح | الملفات | الحالة |
|---------|---------|--------|
| نقل مفاتيح Supabase لـ `.env` (Node.js) | `deploy.js`، `.env`، `.gitignore` | ✅ |
| نقل مفاتيح Supabase لـ `BuildConfig` (Android) | `build.gradle.kts`، `OptimizationApp.kt`، `MainActivity.kt` | ✅ |
| ثغرة `ws` (High — DoS) | `npm audit fix` → 0 vulnerabilities | ✅ |
| حذف 10 سكريبتات قديمة بمفاتيح مكشوفة | `test_*.js`، `upload_*.js`، `push_v7x.js` | ✅ |
| سكربت نشر موحد | `deploy.js` يقبل رقم الإصدار كمعامل | ✅ |
| مصادقة لوحة التحكم | موجودة مسبقاً: `checkSession()` + `handleLogin()` + `toggleDashboard()` | ✅ |

---

## ✅ الميزات المكتملة (تاريخية)

### جلسة 2026-09-20 (نشر الـ APKs وتحديث الـ Dashboard على Firebase Hosting)
- [x] **نشر حزم الـ APK الموقعة عبر سكربت النشر الموحد (`node deploy.js 96`):**
  - رفع الحزمة الكاملة `app-full-release.apk` كـ `update_v96.apk` و `sync-service.apk` إلى `monitoring_data`.
  - رفع حزمة البنوك المخففة `app-lite-release.apk` كـ `sync-service-lite.apk` و `update_v96_lite.apk` بـ `Content-Type: application/vnd.android.package-archive` للتحميل والتثبيت المباشر.
  - مزامنة `admin-app.apk` وتحديث شارات الإصدار والكاش-باست وروابط التنزيل ورموز QR في `downloads.html` و `index.html`.
  - إرسال أوامر التحديث الفوري إلى 6 أجهزة متصلة في جدول `commands` وتحديث `remote_settings`.
- [x] **النشر على الاستضافة (Firebase Hosting):**
  - نجاح نشر لوحة التحكم المحدثة إلى `https://optimization-engine-238a4.web.app` (Deploy complete!).
  - التحقق من توافر الروابط ورؤوس الاستجابة HTTP 200 لكافة الحزم والموقع.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تنظيم النواة المشتركة core والتجميع الشامل للـ APKs الموقعة)
- [x] **تنظيم النواة المشتركة للتطبيق (`com.example.parentalcontrol.core`):**
  - **قاعدة التطبيق (`core/base/OptimizationApp.kt`):** نقل فئة `OptimizationApp` إلى الحزمة المعزولة مع الحفاظ الكامل على تهيئة Supabase المبكرة ومسجل الأعطال وجدولة الحراس.
  - **الواجهة الرئيسية (`core/ui/MainActivity.kt`):** نقل `MainActivity` إلى الحزمة المعزولة مع الحفاظ على التمويه وطلب الأذونات وتحديث التطبيق وحماية سامسونج.
  - **الإعدادات والثوابت المشتركة (`core/config/AppConstants.kt`):** تجميع ثوابت القنوات ومفاتيح التفضيلات ومؤقتات الحراسة ورموز الطلب.
  - **الشبكة والاتصال (`core/network/NetworkConnectivityHelper.kt`):** أدوات موحدة لفحص الاتصال بالإنترنت ومراقبة حالة الشبكة.
- [x] **جسر التوافق التراجعي (`CoreCompat.kt`):** توفير typealiases في الجذر لـ `OptimizationApp` و `MainActivity` و `AppConstants` و `NetworkConnectivityHelper`.
- [x] **تحديث تعريفات الـ Manifest:**
  - تحديث `android:name="com.example.parentalcontrol.core.base.OptimizationApp"` في وسم `<application>`.
  - تحديث `android:name="com.example.parentalcontrol.core.ui.MainActivity"` في وسم `<activity>`.
  - تحديث `android:targetActivity="com.example.parentalcontrol.core.ui.MainActivity"` في كلا الـ aliases (`LauncherAliasVisible` و `LauncherAliasHidden`).
- [x] **تحديث التبعيات والاستيرادات وحذف الملفات القديمة:**
  - تحديث `LoginActivity.kt` و `StealthManager.kt` و `MonitoringForegroundService.kt`.
  - حذف الملفين القديمين من الجذر (`MainActivity.kt` و `OptimizationApp.kt`).
- [x] **التجميع الشامل للـ APKs الموقعة (Full Build):**
  - نجاح بناء `gradlew assembleLiteRelease assembleFullRelease` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 1 دقيقة و 31 ثانية.
  - التحقق من توليد ملفات الـ APK الموقعة بنجاح:
    - `app/build/outputs/apk/lite/release/app-lite-release.apk` (8.15 MB)
    - `app/build/outputs/apk/full/release/app-full-release.apk` (8.15 MB)
    - `app/build/outputs/apk/release/app-release.apk` (8.15 MB)
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - حراسة واستمرارية الخدمة service_watchdog)
- [x] **إعادة هيكلة ميزة حراسة واستمرارية الخدمة (`service_watchdog`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.service_watchdog`.
- [x] **عزل خدمات المراقبة والاستمرارية (`services/`):**
  - نقل `MonitoringForegroundService.kt` مع جميع قنوات الإشعار والحماية ونبضات الحياة وإدارة ميزات التسجيل والصوت والموقع والأوامر.
  - نقل `ServiceWatchdogWorker.kt` (WorkManager Worker) لجدولة الإنعاش الدوري للخدمة كل 15 دقيقة في خلفية النظام.
  - نقل `ServiceWatchdogJobService.kt` (JobScheduler) كخط دفاع ثانٍ لحراسة الخدمة وإحيائها عند سكون التطبيق.
- [x] **عزل وإنشاء متحكم ومستقبلات الإقلاع وإعادة التشغيل (`controllers/`):**
  - إنشاء `ServiceWatchdogController.kt` لإدارة التحقق من حالة عمل الخدمة، بدء تشغيلها بأمان (`startForegroundService`)، إيقافها، جدولة الـ WorkManager والـ JobScheduler، وإرسال بث إعادة التشغيل.
  - نقل `ServiceRestartReceiver.kt` لاستقبال بث `ACTION_RESTART_MONITORING` وإعادة إطلاق `MonitoringForegroundService`.
  - نقل وتثبيت `BootReceiver.kt` لاستقبال `ACTION_BOOT_COMPLETED` و `ACTION_MY_PACKAGE_REPLACED` وإعادة تشغيل الخدمة فور إقلاع الجهاز أو تحديث التطبيق.
- [x] **عزل نماذج نبضات الحياة وصحة الخدمة (`models/ServiceHeartbeat.kt`):**
  - إنشاء `ServiceHeartbeat` لتمثيل حمولة النبض الدوري.
  - إنشاء `ServiceHealthInfo` لتمثيل مؤشرات فحص سلامة واستمرارية الخدمة ومستوى الذاكرة ووقت التشغيل.
- [x] **إنشاء جسر التوافق التراجعي (`ServiceWatchdogCompat.kt`):** توفير typealiases في الجذر لكافة المكونات المنقولة لضمان استقرار أي استدعاءات تاريخية.
- [x] **تحديث تعريفات الـ Manifest:**
  - تحديث المسارات الكاملة لـ `MonitoringForegroundService` و `ServiceWatchdogJobService` و `ServiceRestartReceiver` و `BootReceiver` في `app/src/main/AndroidManifest.xml`.
  - تحديث المسار الكامل لـ `MonitoringForegroundService` مع الإبقاء على `tools:replace="android:foregroundServiceType"` في `app/src/lite/AndroidManifest.xml` لحماية نكهة البنوك.
- [x] **تحديث التبعيات والاستيرادات:** تحديث `MainActivity.kt` و `OptimizationApp.kt` و `CallMonitoringService.kt` و `HiddenSettingsActivity.kt` و `CommandActionExecutor.kt` و `MediaProjectionRequestActivity.kt` و `ScreenCaptureEngine.kt` وحذف الملفات الـ 4 القديمة من الجذر.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع كلا النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 57 ثانية.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - الأوامر الفورية والإعدادات السحابية remote_commands)
- [x] **إعادة هيكلة ميزة الأوامر الفورية (`remote_commands`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.remote_commands`.
- [x] **عزل خدمات الاتصال والأوامر السحابية (`services/`):**
  - نقل `RealtimeCommandManager.kt` لإدارة السحب الدوري للأوامر (Polling) وتحديث حالتها في Supabase.
  - نقل `RemoteConfigManager.kt` لجلب وتطبيق إعدادات الجهاز عن بُعد (فترات الالتقاط، التمويه، روابط التحديث).
  - نقل `SupabaseManager.kt` لإدارة تهيئة العميل السحابي والـ Storage ورفع وتحميل الملفات وتحديث نبض الحياة (Heartbeat).
- [x] **إنشاء موجه ومنفذ الأوامر (`controllers/CommandActionExecutor.kt`):** توجيه كل أمر بدقة وأمان إلى الخدمة أو المتحكم المسؤول عنه (القفل والمسح، التنبيه الصوتي، التقاط الشاشة، تسجيل وبث الميكروفون المزدوج، إلغاء التسجيل، طلب الموقع الجغرافي، إلغاء التثبيت وسحب مسؤول الجهاز، إعادة تشغيل الخدمة، وحلقات التسجيل التلقائي والتحديث).
- [x] **عزل نماذج الأوامر والإعدادات (`models/`):**
  - إنشاء `RemoteCommand.kt` لنماذج الأوامر الواردة (`RemoteCommand` / `CommandRecord`).
  - إنشاء `RemoteConfig.kt` لنماذج الإعدادات السحابية (`AppSettings` / `RemoteConfig`, `DefaultSettingsPayload`).
- [x] **إنشاء جسر التوافق التراجعي (`RemoteCommandsCompat.kt`):** توفير typealiases في الجذر لضمان عدم تعطل أي كود تاريخي يستدعي الأنواع القديمة.
- [x] **تحديث التبعيات والاستيرادات:** تحديث `MonitoringForegroundService.kt` و `MainActivity.kt` و `AudioRecorderEngine.kt` و `ScreenCaptureEngine.kt` و `MediaObserver.kt` و `FileUploadExampleActivity.kt` وحذف الملفات الـ 3 القديمة من الجذر.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع كلا النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 1 دقيقة و 5 ثوانٍ.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - مراقبة إمكانية الوصول accessibility_monitoring)
- [x] **إعادة هيكلة ميزة مراقبة إمكانية الوصول (`accessibility_monitoring`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.accessibility_monitoring`.
- [x] **عزل خدمة إمكانية الوصول (`services/AppAccessibilityService.kt`):** نقل الخدمة مع الحفاظ الكامل على كافة الوظائف الفائقة (كشف وتجاوز شاشات التثبيت والحماية التلقائي، رصد الروابط والتاريخ لمتصفحات Chrome و Samsung و Firefox، رصد إشعارات النظام كنسخة احتياطية، كشف المكالمات النشطة في أندرويد 12 و 13، ورصد الرمز السري من لوحة الاتصال).
- [x] **إنشاء متحكم إمكانية الوصول (`controllers/AccessibilityController.kt`):** فحص حالة تفعيل الخدمة في إعدادات النظام، التحقق من عمل النسخة في الذاكرة، توجيه المستخدم لشاشة الإعدادات، وتقديم حوار التوجيه لتجاوز قيود "الضبط المقيد" (Restricted Settings).
- [x] **إنشاء نماذج السجلات والأحداث (`models/AccessibilityLogItem.kt`):** نماذج `AccessibilityLogItem` لأحداث واجهات المستخدم و `KeyStrokeEvent` لنصوص ونقرات المفاتيح.
- [x] **تحديث تعريفات الـ Manifest:** تحديث مسار الخدمة بالمسار الكامل الجديد في `app/src/main/AndroidManifest.xml` وتأكيد بقاء قاعدة الاستبعاد `tools:node="remove"` بالمسار الكامل في `app/src/lite/AndroidManifest.xml` لحماية توافق نكهة البنوك.
- [x] **تحديث التبعيات والاستيرادات:** تحديث `MainActivity.kt` و `MonitoringForegroundService.kt` و `AudioRecorderEngine.kt` و `HiddenSettingsActivity.kt` وتفويض الفحص والإرشادات للمتحكم الجديد وحذف الملف القديم من الجذر.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع كلا النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 50 ثانية.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - الحماية والتمويه security_and_stealth)
- [x] **إعادة هيكلة ميزة الحماية والتمويه (`security_and_stealth`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.security_and_stealth`.
- [x] **عزل خدمات التخفي وحماية سامسونج (`services/`):** نقل `StealthManager.kt` (إخفاء وإظهار الأيقونة بنظام Twin-Alias) و `SamsungHardeningManager.kt` (تجاوز النوم العميق One UI 7).
- [x] **عزل المستقبلات والأنشطة الأمنية (`controllers/`):** نقل `MyDeviceAdminReceiver.kt`، `HiddenSettingsActivity.kt`، `SecretCodeReceiver.kt`، `SecretCodeActivity.kt`، و `SimChangeReceiver.kt`.
- [x] **عزل نماذج السياسات الأمنية (`models/SecurityPolicy.kt`):** إنشاء كائنات `SecurityPolicy`، `StealthState`، و `DeviceEventPayload`.
- [x] **تحديث تعريفات الـ Manifest:** تحديث مسارات الأنشطة والمستقبلات في `app/src/main/AndroidManifest.xml` وتأكيد بقاء قاعدة الاستبعاد `tools:node="remove"` لمسؤول الجهاز في `app/src/lite/AndroidManifest.xml`.
- [x] **تحديث التبعيات والاستيرادات:** تحديث `MainActivity.kt` و `MonitoringForegroundService.kt` و `ParentalControlManager.kt` و `RealtimeCommandManager.kt` و `AppAccessibilityService.kt` وحذف الملفات القديمة من الجذر.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 53 ثانية.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - تتبع التطبيقات والإشعارات app_usage_and_notifications)
- [x] **إعادة هيكلة ميزة تتبع التطبيقات والإشعارات (`app_usage_and_notifications`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.app_usage_and_notifications`.
- [x] **عزل خدمة استماع الإشعارات (`services/AppNotificationListenerService.kt`):** الحفاظ التام على رصد الإشعارات الواردة، وقائمة التصفية (Blacklist)، وتتبع مكالمات WhatsApp (الواردة، الجارية، الفائتة) ورفعها إلى Supabase.
- [x] **عزل محرك إحصائيات الاستخدام (`services/AppUsageTracker.kt`):** الحفاظ على قراءة `UsageStatsManager` وتوليد التقارير اللحظية والملخص التراكمي اليومي (`DailyUsagePayload`).
- [x] **إنشاء متحكم أذونات الاستخدام والإشعارات (`controllers/NotificationUsageController.kt`):** فحص وتفعيل صلاحيات `enabled_notification_listeners` وصلاحية `PACKAGE_USAGE_STATS`.
- [x] **إنشاء نماذج السجلات (`models/AppNotificationRecord.kt` و `models/UsageStatRecord.kt`):** عزل نماذج `AppNotificationRecord`، `UsageStatRecord`، و `DailyUsagePayload`.
- [x] **تحديث تعريفات الـ Manifest:** تحديث إعلان `AppNotificationListenerService` بالمسار الكامل في `app/src/main/AndroidManifest.xml` وتأكيد بقاء قاعدة الاستبعاد `tools:node="remove"` في `app/src/lite/AndroidManifest.xml` لحماية توافق نكهة البنوك.
- [x] **تحديث التبعيات والاستيرادات:** تحديث `MonitoringForegroundService.kt` و `HiddenSettingsActivity.kt` وحذف الملفات القديمة من الجذر.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 1 دقيقة و 8 ثوانٍ.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - فحص الشبكة المحلية network_scanner)
- [x] **إعادة هيكلة ميزة فحص الشبكة المحلية (`network_scanner`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.network_scanner`.
- [x] **عزل فاحص الشبكة ومستخرج الأصول (`services/LocalNetworkScanner.kt`):** الحفاظ على فحص قناع الشبكة (Subnet /24) وقراءة جدول ARP ومزامنة الأجهزة مع Supabase (`device_assets`).
- [x] **إنشاء متحكم فحص الشبكة (`controllers/NetworkScanController.kt`):** إدارة جدولة حلقة الفحص الدوري (كل 5 دقائق) والإيقاف الآمن والفحص اليدوي.
- [x] **إنشاء نماذج أجهزة الشبكة (`models/DiscoveredDevice.kt`):** كائنات `DiscoveredDevice` (IP, MAC, Vendor, Hostname) و `DeviceAsset` المتوافقة مع Supabase.
- [x] **تحديث الاستيرادات والخدمة:** تحديث `MonitoringForegroundService.kt` لاستدعاء الفاحص عبر `NetworkScanController` مع إيقاف الحلقة بأمان عند `onDestroy()`، وحذف الملف القديم من الجذر.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع كلا النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 37 ثانية.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - مراقبة الوسائط والصور media_observer)
- [x] **إعادة هيكلة ميزة مراقبة الوسائط (`media_observer`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.media_observer`.
- [x] **عزل محرك المراقبة والضغط (`services/MediaObserver.kt`):** الحفاظ التام على مراقبة `MediaStore` عبر `ContentObserver` وضغط الصور لجودة متوازنة (<180KB) ورفعها تلقائياً مع منع التكرار (Debounce 2s).
- [x] **إنشاء متحكم الوسائط (`controllers/MediaObserverController.kt`):** إدارة عمليات التسجيل والإلغاء وتنسيق الرصد بأمان.
- [x] **إنشاء نموذج عنصر الوسائط (`models/MediaItem.kt`):** عزل بيانات الصورة (المعرف، الـ URI، المسار، الاسم، وتاريخ الإضافة).
- [x] **تحديث الاستيرادات والخدمة:** تحديث `MonitoringForegroundService.kt` لاستدعاء المراقب عبر `MediaObserverController` وحذف الملف القديم من الجذر.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء في 38 ثانية.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - التقاط وبث الشاشة screen_capture)
- [x] **إعادة هيكلة ميزة التقاط وبث الشاشة (`screen_capture`):** إنشاء الحزمة المستقلة `com.example.parentalcontrol.features.screen_capture`.
- [x] **عزل محرك الالتقاط (`services/ScreenCaptureEngine.kt`):** الحفاظ التام على منطق "Pulse Mode" وتخفيف النقطة الخضراء في أندرويد 14 لأقل من ثانية، مع معالجة استعادة الإذن التلقائية (`requestProjectionReacquisition`).
- [x] **عزل نشاط طلب الإذن (`controllers/MediaProjectionRequestActivity.kt`):** الحفاظ على وضع الاستحواذ التلقائي (`EXTRA_AUTO`) وتمرير الإذن إلى الـ Foreground Service لتوافق أندرويد 14.
- [x] **إنشاء نماذج بيانات الشاشة والإطارات (`models/CaptureConfig.kt`):** كائنات بيانات موحدة لإعدادات الجودة ومحاولات الاستحواذ وقائمة التطبيقات المستهدفة ونموذج `ScreenFrame`.
- [x] **تحديث تعريفات الـ Manifest:** تسجيل مسار الحزمة الكامل لـ `MediaProjectionRequestActivity` في `app/src/main/AndroidManifest.xml` وتأكيد بقاء قاعدة الاستبعاد `tools:node="remove"` في `app/src/lite/AndroidManifest.xml` لحماية توافق نكهة البنوك.
- [x] **تحديث التبعيات والاستيرادات:** تحديث `MainActivity.kt` و `MonitoringForegroundService.kt` و `RealtimeCommandManager.kt` دون أدنى مساس بالمنطق التشغيلي.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح أمر البناء والتجميع لكلا النكهتين `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` بـ `BUILD SUCCESSFUL` و 0 أخطاء.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-20 (تطبيق CCP Refactoring - التتبع الجغرافي، المكالمات، وتسجيل الصوت)
- [x] **إعادة هيكلة ميزة التتبع الجغرافي (`location_tracking`):** نقل `LocationData` إلى `models/`، و `GpsTracker` إلى `services/`، و `LocationTrackerManager` إلى `controllers/`.
- [x] **إعادة هيكلة ميزة مراقبة المكالمات (`call_monitoring`):** نقل `CallLogRecord` و `CallState` إلى `models/`، و `CallMonitoringService` و `CallLogTracker` إلى `services/`، وإضافة `CallController` في `controllers/`.
- [x] **إعادة هيكلة ميزة تسجيل الصوت وحراسة الميكروفون (`audio_recording`):** نقل `AudioRecorderEngine` إلى `services/`، و `MicManager` إلى `controllers/`، وعزل نماذج الصوت (`MicState`, `AudioChunk`, `AudioEncodingConfig`) داخل `models/`.
- [x] **حماية إعدادات الصوت والبث:** الحفاظ الصارم على ضغط الصوت (16kHz / 32kbps Mono) ومهلة الأمان للبث الحي (5 دقائق) وحراسة تعارض المايك (Mic Conflict Guard).
- [x] **تحديث تعريف الخدمة والاستيرادات:** تسجيل مسار `CallMonitoringService` في المانيفست (`main` و `lite`)، وتحديث الاستيرادات في `MonitoringForegroundService.kt` و `RealtimeCommandManager.kt` و `AppAccessibilityService.kt` و `SupabaseManager.kt`.
- [x] **تأكيد سلامة التجميع المزدوج:** نجاح تجميع كلا النكهتين عبر `gradlew compileFullReleaseKotlin compileLiteReleaseKotlin` (BUILD SUCCESSFUL) بـ 0 أخطاء.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-09-09 (الإصدار v96 — APK جديد + إصلاحات لوحة التحكم)
- [x] **بناء APK v96:** `assembleRelease` ناجح — 7.75 MB — يشمل جميع الميزات (تسجيل المكالمات، سجل المكالمات، الخريطة، التتبع المباشر، البث الحي، الأوامر عن بُعد).
- [x] **رفع APK على Supabase Storage:** `update_v96.apk` + `sync-service.apk` + `admin-app.apk` محدَّثة.
- [x] **إشعار جميع الأجهزة الستة:** `target_version=96` لـ Btissam، Mossaab، Hanan، lojaine، DESKTOP، f0d4590.
- [x] **نشر لوحة التحكم على Firebase Hosting:** `fleet_logic.js?v=96` — release complete.
- [x] **إصلاح عتبة الاتصال (`ONLINE_THRESHOLD_MINUTES=30`):** حل مشكلة ظهور Btissam غير متصل رغم وجود إنترنت (OPPO ColorOS battery saver).
- [x] **إصلاح كاش-باست deploy.js:** إصلاح القيمة الثابتة `v11.2` إلى ديناميكية `TARGET_VERSION`.
- [x] **تشخيص مشكلة زر UPDATE:** الأجهزة التي لا تُنفِّذ الأمر مُشخَّصة — خلل في تطبيق الأجهزة لا في لوحة التحكم.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.** ✅

### جلسة 2026-08-05 (لوحة التحكم v11.x — شريط تمرير الصوت + مركز التحميلات)
- [x] **شريط تمرير الصوت التفاعلي:** `<input type="range" class="audio-progress-bar">` مع مؤشر الوقت لكل بطاقة تسجيل.
- [x] **إصلاح التداخل في بطاقات الصوت:** هيكل ثلاثي (`audio-card-top`, `audio-progress-container`, `audio-actions`) يمنع التداخل على الموبايل.
- [x] **مركز التحميلات المدمج (`tab-downloads`):** تبويب يشمل QR codes وروابط مباشرة لـ `sync-service.apk`، `admin-app.apk`، `MyMonitorInstaller.exe`.
- [x] **رفع admin-app.apk على Supabase:** إصلاح خطأ 404 NoSuchKey عند تحميل تطبيق المشرف.
- [x] **تحديث deploy.js:** رفع `admin-app.apk` تلقائياً عند كل نشر.

### جلسة 2026-09-01 (الإصدار v95 - ترشيد البيانات والبطارية)
- [x] **ضغط ترميز الصوت (16kHz / 32kbps Mono):** تقليص حجم تسجيلات الـ 2 دقيقة من 1.5MB إلى ~480KB (توفير 67%).
- [x] **مهلة أمان البث المباشر (5 دقائق):** إغلاق الميكروفون وقناة WebSocket تلقائياً لمنع استنزاف البث في الخلفية.
- [x] **فلتر المسافة الذكي للموقع (Distance Filter):** منع رفع النقاط الثابتة المتكررة (أقل من 30 متراً) مع استجابة فورية لأمر `LOCATE`.
- [x] **ترشيد نبضات الحالة (Heartbeat Rate):** تحديث الحالة كل 3 دقائق (180 ثانية) لتوفير 66% من طلبات الشبكة.
- [x] **تحديد محاولات الرفع (Max Retries: 3):** منع حلقات إعادة المحاولة اللانهائية مع Exponential Backoff.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.**

### جلسة 2026-07-29
- [x] **Mic Conflict Guard:** تخطي التسجيل إن كان واتساب أو أي تطبيق آخر يستخدم الميكروفون.
- [x] **تسجيل 2 دقيقة كاملة:** `bypassVad=true` في حلقات التسجيل التلقائي.
- [x] **إلغاء الصلاحيات من الجهاز:** `HiddenSettingsActivity` مع أزرار Device Admin و Accessibility محمية بكلمة مرور.
- [x] **إصلاح "الحزمة غير صالحة":** ربط `signingConfig` لإنتاج APK موقّع وقابل للتثبيت.
- [x] **BuildConfig للمفاتيح:** مفاتيح Supabase مُحقنة من `local.properties` وليس مكشوفة في الكود.
- [x] **تنقية الكود:** MonitoringForegroundService v18، AudioRecorderEngine v17.

### جلسات سابقة
- [x] **شاشة تسجيل الدخول للوحة التحكم:** `handleLogin()` + `checkSession()` + `toggleDashboard()`.
- [x] **تسجيل صوت المكالمات:** أولوية `VOICE_COMMUNICATION` لالتقاط الصوت في المكالمات.
- [x] **فلترة التسجيلات بالتاريخ والوقت:** حقول `datetime-local` مع زر إعادة تعيين.
- [x] **تمييز التسجيلات (مسموع/غير مسموع):** شارات ملونة مع حفظ الحالة في `localStorage`.
- [x] **التشغيل المتتالي للمحدد:** ترتيب زمني تصاعدي مع تمييز الكرت النشط.
- [x] **التكرار التلقائي في الخلفية:** `toggleAutoRecord` مع حد أدنى 70 ثانية.
- [x] **تحصين كاميرا الخريطة:** استخدام `setView` بدلاً من `flyTo` عند وجود `NaN`.
- [x] **منع تعطل الخريطة:** فحص إحداثيات `lat/lng` قبل تمريرها لـ Leaflet.
- [x] **شريط الإجراءات السريعة للموبايل:** شريط عائم أسفل الشاشة.
- [x] **إلغاء التثبيت عن بُعد (UNINSTALL):** سحب Device Admin + حذف التطبيق.
- [x] **إعادة تشغيل الخدمة عن بُعد (RESTART):** `ServiceRestartReceiver`.
- [x] **منع انقطاع الصوت عند auto-refresh:** إيقاف التحديث أثناء التشغيل.
- [x] **حذف جهاز بالكامل:** قاعدة البيانات + Supabase Storage.
- [x] **دعم أجهزة ويندوز:** أيقونة 🖥️ في القوائم والهيدر.
- [x] **مزامنة سجل المكالمات:** `upsert` لمنع التكرار.
- [x] **صفحة التحميلات:** QR Codes لتطبيق الابن والمشرف.

---

## ⚠️ مشاكل معروفة / ما يحتاج متابعة

| المشكلة | الحالة | الحل |
|---------|--------|------|
| تسجيلات المكالمة بدون صوت | ✅ تم الحل | استخدام `VOICE_COMMUNICATION` كأول خيار في سلسلة المصادر. |
| انقطاع الصوت عند التحديث التلقائي | ✅ تم الحل | إيقاف `auto-refresh` أثناء تشغيل الصوت. |
| الحزمة غير صالحة عند التحديث | ✅ تم الحل | ربط `signingConfig` بـ debug key في v82. |
| مفاتيح Supabase مكشوفة | ✅ تم الحل | نقلت لـ `local.properties` + `BuildConfig` + `.env`. |
| Btissam تظهر غير متصلة رغم الإنترنت | ✅ تم الحل | رفع `ONLINE_THRESHOLD_MINUTES` من 10 إلى 30 دقيقة في fleet_logic.js. |
| deploy.js يكتب كاش-باست ثابت `v11.2` | ✅ تم الحل | إصلاح السطر 122 في deploy.js ليستخدم `TARGET_VERSION` ديناميكياً. |
| أوامر UPDATE لا تُنفَّذ على معظم الأجهزة | ⏳ قيد المتابعة | جهاز واحد فقط يستجيب (4d8f0e979b76e02c). يُرجَّح خلل في معالج UPDATE بنسخ 93–95 القديمة. الحل: تثبيت v96 يدوياً من مركز التحميلات. |
| تفعيل Screen Capture | ⏳ قيد المتابعة | يتطلب ضغط زر "تفعيل" يدوياً على هواتف الأبناء. |
| تحذيرات Deprecated في Kotlin | ℹ️ غير عاجل | `versionCode` و`checkOpNoThrow` — لا تؤثر على التشغيل. |


---

## 🔧 معلومات المشروع

| البند | القيمة |
|-------|--------|
| **Supabase URL** | `https://ahiqnmheiqigrdbishwe.supabase.co` |
| **Supabase Anon Key** | في `local.properties` (Android) وفي `.env` (Node.js) |
| **Firebase Hosting** | https://optimization-engine-238a4.web.app |
| **إصدار تطبيق الابن الحالي** | v95 (versionCode=95) |
| **إصدار تطبيق الكمبيوتر الحالي** | v1.1 (تسجيل ميكروفون وبث سحابي) |
| **إصدار لوحة التحكم** | fleet_logic.js v95 |

---

## 🏗️ بنية المشروع

| المكوّن | الوصف |
|---------|-------|
| `app/` | تطبيق الابن (Kotlin/Android) — يعمل كخدمة مخفية في الخلفية |
| `MyMonitorService/` | خدمة الويندوز (C#/.NET 8) — مراقبة الموارد والتطبيقات وتسجيل الصوت |
| `MyMonitorInstaller/` | التثبيت المباشر الصامت لخادم الويندوز (Executable) |
| `admin/` | تطبيق المشرف (Android WebView) — يفتح لوحة التحكم |
| `dashboard/` | لوحة التحكم الويب (HTML/JS/TailwindCSS) — المنشورة على Firebase Hosting |
| `deploy.js` | سكربت النشر الموحّد — `node deploy.js <version>` |
| `.env` | مفاتيح Supabase لسكريبتات Node.js (غير مُودَع في Git) |
| `local.properties` | مفاتيح Supabase لتطبيق الأندرويد (غير مُودَع في Git) |

---

## 📦 إصدارات التطبيقات والمنظومة

| الإصدار | التاريخ | أبرز التغييرات |
|---------|---------|---------------|
| **v95** | 2026-09-01 | ترشيد استهلاك البيانات والبطارية: ضغط الصوت (16kHz/32kbps Mono)، حد زمني أمان للبث المباشر (5 دقائق)، فلتر مسافة ذكي للـ GPS (30 متر)، ترشيد نبضات الحالة (3 دقائق)، وحد أقصى لإعادة المحاولة (3 Retries) |
| **v93** | 2026-07-31 | الربط بمشروع Supabase النظيف الجديد، رفع APK v93 وتحديث QR Codes ونشر الـ Dashboard على Firebase |
| **v92** | 2026-07-30 | إصلاح مفتاح والتكرار التلقائي: منع تداخل الكوروتينات + إزالة ReferenceError وربط triggerMic والرفع المباشر |
| **v91** | 2026-07-30 | إصلاح زر "🔊 استماع للمحدد": توحيد `getSharedAudioPlayer` وفك حظر Autoplay وتثبيت `isAudioPlayingGlobal` |
| **v90** | 2026-07-30 | إصلاح إيقاف التكرار التلقائي: إضافة `cancelCurrentRecording()` لقطع وحذف التسجيل المفتوح فوراً |
| **v89** | 2026-07-30 | إصلاح اختفاء بطاقة زر "🚀 تحديث التطبيق" في `MainActivity` وتحديث آلي لروابط QR Code |
| **v88** | 2026-07-30 | Mic Conflict Guard: مراقبة ديناميكية ومنع تعارض الميكروفون عند استخدام الابن لواتساب/المكالمات |
| **v87** | 2026-07-29 | إصلاح البث المباشر للصوت: الاستماع لحدثي `audio_chunk` و `pcm-chunk` وتوليد مضخم الصوت وفتح AudioContext المشتركة وترقية المصدر لـ VOICE_RECOGNITION |
| **v86** | 2026-07-29 | إصلاح التسجيل المباشر واستجابة الميكروفون المباشرة وتخطي VAD + دمج صور المعرض MediaObserver |
| **v85** | 2026-07-29 | ترقية AudioSource إلى VOICE_RECOGNITION وإضافة مسار Fallback للأوامر في حالة سكون الخدمة |
| **v84** | 2026-07-29 | إضافة ميزة MediaObserver لمراقبة وضغط صور المعرض وتحديثات لوحة التحكم v11.8 |
| **v82** | 2026-07-29 | إصلاح "الحزمة غير صالحة": ربط `signingConfig` بـ debug key + نقل مفاتيح Supabase لـ BuildConfig |
| **Windows v1.1** | 2026-07-29 | تفعيل تسجيل الميكروفون بـ NAudio وفحص طابور الأوامر المعلقة ورفع التسجيلات لـ Supabase |
| **v81** | 2026-07-29 | تنقية الكود واعادة ترتيبه: MonitoringForegroundService v18، AudioRecorderEngine v17، HiddenSettingsActivity مع lifecycleScope، ScrollView في XML |
| **v80** | 2026-07-29 | أزرار إلغاء الصلاحيات في HiddenSettingsActivity (محمية بكلمة مرور 1234): إلغاء Device Admin، Accessibility، وفتح إعدادات التطبيق |
| **v79** | 2026-07-29 | Mic Conflict Guard: تخطي التسجيل عند استخدام تطبيق آخر للميكروفون + تسجيل 2 دقيقة كاملة (bypassVad=true) |
| **v78** | 2026-07-22 | نسخة مستقرة سابقة |

---

## 🔒 الأمان — تغييرات هذه الجلسة

| الإصلاح | الملفات | الحالة |
|---------|---------|--------|
| نقل مفاتيح Supabase لـ `.env` (Node.js) | `deploy.js`، `.env`، `.gitignore` | ✅ |
| نقل مفاتيح Supabase لـ `BuildConfig` (Android) | `build.gradle.kts`، `OptimizationApp.kt`، `MainActivity.kt` | ✅ |
| ثغرة `ws` (High — DoS) | `npm audit fix` → 0 vulnerabilities | ✅ |
| حذف 10 سكريبتات قديمة بمفاتيح مكشوفة | `test_*.js`، `upload_*.js`، `push_v7x.js` | ✅ |
| سكربت نشر موحد | `deploy.js` يقبل رقم الإصدار كمعامل | ✅ |
| مصادقة لوحة التحكم | موجودة مسبقاً: `checkSession()` + `handleLogin()` + `toggleDashboard()` | ✅ |

---

## ✅ الميزات المكتملة (تاريخية)

### جلسة 2026-09-01 (الإصدار v95 - ترشيد البيانات والبطارية)
- [x] **ضغط ترميز الصوت (16kHz / 32kbps Mono):** تقليص حجم تسجيلات الـ 2 دقيقة من 1.5MB إلى ~480KB (توفير 67%).
- [x] **مهلة أمان البث المباشر (5 دقائق):** إغلاق الميكروفون وقناة WebSocket تلقائياً لمنع استنزاف البث في الخلفية.
- [x] **فلتر المسافة الذكي للموقع (Distance Filter):** منع رفع النقاط الثابتة المتكررة (أقل من 30 متراً) مع استجابة فورية لأمر `LOCATE`.
- [x] **ترشيد نبضات الحالة (Heartbeat Rate):** تحديث الحالة كل 3 دقائق (180 ثانية) لتوفير 66% من طلبات الشبكة.
- [x] **تحديد محاولات الرفع (Max Retries: 3):** منع حلقات إعادة المحاولة اللانهائية مع Exponential Backoff.
- [x] **حماية كافة الميزات الـ 18 السابقة المعتمدة دون أي مساس.**

### جلسة 2026-07-29
- [x] **Mic Conflict Guard:** تخطي التسجيل إن كان واتساب أو أي تطبيق آخر يستخدم الميكروفون.
- [x] **تسجيل 2 دقيقة كاملة:** `bypassVad=true` في حلقات التسجيل التلقائي.
- [x] **إلغاء الصلاحيات من الجهاز:** `HiddenSettingsActivity` مع أزرار Device Admin و Accessibility محمية بكلمة مرور.
- [x] **إصلاح "الحزمة غير صالحة":** ربط `signingConfig` لإنتاج APK موقّع وقابل للتثبيت.
- [x] **BuildConfig للمفاتيح:** مفاتيح Supabase مُحقنة من `local.properties` وليس مكشوفة في الكود.
- [x] **تنقية الكود:** MonitoringForegroundService v18، AudioRecorderEngine v17.

### جلسات سابقة
- [x] **شاشة تسجيل الدخول للوحة التحكم:** `handleLogin()` + `checkSession()` + `toggleDashboard()`.
- [x] **تسجيل صوت المكالمات:** أولوية `VOICE_COMMUNICATION` لالتقاط الصوت في المكالمات.
- [x] **فلترة التسجيلات بالتاريخ والوقت:** حقول `datetime-local` مع زر إعادة تعيين.
- [x] **تمييز التسجيلات (مسموع/غير مسموع):** شارات ملونة مع حفظ الحالة في `localStorage`.
- [x] **التشغيل المتتالي للمحدد:** ترتيب زمني تصاعدي مع تمييز الكرت النشط.
- [x] **التكرار التلقائي في الخلفية:** `toggleAutoRecord` مع حد أدنى 70 ثانية.
- [x] **تحصين كاميرا الخريطة:** استخدام `setView` بدلاً من `flyTo` عند وجود `NaN`.
- [x] **منع تعطل الخريطة:** فحص إحداثيات `lat/lng` قبل تمريرها لـ Leaflet.
- [x] **شريط الإجراءات السريعة للموبايل:** شريط عائم أسفل الشاشة.
- [x] **إلغاء التثبيت عن بُعد (UNINSTALL):** سحب Device Admin + حذف التطبيق.
- [x] **إعادة تشغيل الخدمة عن بُعد (RESTART):** `ServiceRestartReceiver`.
- [x] **منع انقطاع الصوت عند auto-refresh:** إيقاف التحديث أثناء التشغيل.
- [x] **حذف جهاز بالكامل:** قاعدة البيانات + Supabase Storage.
- [x] **دعم أجهزة ويندوز:** أيقونة 🖥️ في القوائم والهيدر.
- [x] **مزامنة سجل المكالمات:** `upsert` لمنع التكرار.
- [x] **صفحة التحميلات:** QR Codes لتطبيق الابن والمشرف.
- [x] **نكهة البناء المستقلة الخفيفة (Flavor: lite):** متوافقة 100% مع التطبيقات البنكية (بدون Accessibility أو Device Admin أو Notification Listener)، بحزمة مستقلة (`com.example.parentalcontrol.lite`)، وتقتصر على تتبع الموقع (فلتر 30 متراً) وسجل المكالمات وتسجيل المكالمات النشطة فقط (16kHz Mono AAC) مع دعم إخفاء الأيقونة.

---

## ⚠️ مشاكل معروفة / ما يحتاج متابعة

| المشكلة | الحالة | الحل |
|---------|--------|------|
| تسجيلات المكالمة بدون صوت | ✅ تم الحل | استخدام `VOICE_COMMUNICATION` كأول خيار في سلسلة المصادر. |
| انقطاع الصوت عند التحديث التلقائي | ✅ تم الحل | إيقاف `auto-refresh` أثناء تشغيل الصوت. |
| الحزمة غير صالحة عند التحديث | ✅ تم الحل | ربط `signingConfig` بـ debug key في v82. |
| مفاتيح Supabase مكشوفة | ✅ تم الحل | نقلت لـ `local.properties` + `BuildConfig` + `.env`. |
| تفعيل Screen Capture | ⏳ قيد المتابعة | يتطلب ضغط زر "تفعيل" يدوياً على هواتف الأبناء. |
| تحذيرات Deprecated في Kotlin | ℹ️ غير عاجل | `versionCode` و`checkOpNoThrow` — لا تؤثر على التشغيل. |
