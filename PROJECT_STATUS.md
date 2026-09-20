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
| **v96** | 2026-09-09 | بناء APK جديد يضم تسجيل المكالمات + سجل المكالمات + الخريطة + التتبع المباشر. إصلاح عتبة الاتصال (ONLINE_THRESHOLD_MINUTES=30). إصلاح كاش-باست ديناميكي في deploy.js. رفع على Supabase + نشر Firebase. |
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
