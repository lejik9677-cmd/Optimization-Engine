# Workspace Rules for Antigravity Agent

## Language Requirements
- **Arabic Output**: All communication, summaries, and walkthroughs must be written in **Arabic** when interacting with Arabic-speaking users.

## Deployment Rules
- **Signed APK only**: Always build `assembleRelease` (not debug). The release build uses `signingConfig = debug` so it produces a signed installable APK.
- **Deploy command**: Use `node deploy.js <version>` — never create new `push_vXX.js` scripts.
- **Version bump**: Increment `versionCode` and `versionName` in `app/build.gradle.kts` before every deployment.
- **Automated Version & QR Sync**: All deployments via `node deploy.js <version>` MUST automatically synchronize direct APK download links, version badges, QR Code generator API URLs (`api.qrserver.com`), and cache-busting query strings in both `downloads.html` and `index.html`.
- **Bust browser cache**: After editing `fleet_logic.js` or CSS, always bump the version query string in `index.html` (e.g. `fleet_logic.js?v=10.5`).

## Security Rules
- **No hardcoded secrets**: Supabase keys must live in `local.properties` (Android) and `.env` (Node.js). Never paste raw JWT tokens in source files.
- **No commit of `.env`**: It is already in `.gitignore`. Never remove it from there.
- **BuildConfig for Android**: Access credentials via `BuildConfig.SUPABASE_URL` and `BuildConfig.SUPABASE_KEY` — never inline strings.

## Audio Vault Formatting
- **Timestamp display**: In the Audio Vault list (`fetchAudio` in `fleet_logic.js`), always display the exact date and time extracted from the filename (`yyyyMMdd_HHmmss`) using the `formatAudioTimestamp()` helper:
  `📅 DD/MM/YYYY - 🕒 HH:MM:SS`

---

## الميزات الأساسية للمشروع — يُمنع حذفها في أي تحديث

1. **تنسيق التاريخ والوقت في التسجيلات**: استخراج الوقت من اسم الملف وعرضه بصيغة `📅 DD/MM/YYYY - 🕒 HH:MM:SS`.
2. **منع تعطل الخريطة (Leaflet Crash Prevention)**: فحص إحداثيات lat/lng وتجنب تمرير قيم NaN أو null.
3. **شريط الإجراءات السريعة للموبايل**: شريط الأزرار العائمة أسفل الشاشة (التقاط، موقع، ميك، تشغيل، خروج).
4. **مزامنة سجل المكالمات**: استخدام `upsert` لمنع تعارض المعرفات وتكرار السجلات.
5. **إلغاء التثبيت عن بُعد (Remote Uninstall)**: زر وأمر UNINSTALL يسحب صلاحيات Device Admin ثم يحذف التطبيق.
6. **إعادة تشغيل الخدمة عن بُعد (Remote Restart)**: زر وأمر RESTART لإعادة MonitoringForegroundService عبر ServiceRestartReceiver.
7. **منع انقطاع الصوت عند التحديث التلقائي**: إيقاف auto-refresh مؤقتاً عند تشغيل أي ملف صوتي.
8. **حذف جهاز بالكامل**: حذف الجهاز من قاعدة البيانات وتطهير ملفاته من Supabase Storage.
9. **ترتيب التشغيل المتتالي**: فرز قائمة التشغيل زمنياً تصاعدياً (من الأقدم للأحدث).
10. **دعم أجهزة ويندوز**: تمييز أجهزة ويندوز بـ 🖥️ وأندرويد بـ 📱 في القوائم والهيدر.
11. **تمييز التسجيلات (مسموع/غير مسموع)**: شارات مسموع (أخضر) / غير مسموع (أزرق)، محفوظة في localStorage['heard_recordings'].
12. **زر استماع للمحدد**: تشغيل التسجيلات المحددة متتالياً بالترتيب الزمني التصاعدي مع تمييز الكرت النشط.
13. **التكرار التلقائي في الخلفية**: toggleAutoRecord يرسل أوامر ويحدث recording_enabled و capture_interval في remote_settings (حد أدنى 70 ثانية).
14. **تحصين كاميرا الخريطة**: عند تحديث الكاميرا، التحقق من getCenter() — إن احتوى NaN استخدم setView بدلاً من flyTo.
15. **فلترة التسجيلات بالتاريخ والوقت**: حقول datetime-local لفلترة التسجيلات بالطابع الزمني المستخرج من اسم الملف مع زر إعادة تعيين.
16. **إلغاء الصلاحيات من تطبيق الابن**: أزرار في HiddenSettingsActivity (محمية بكلمة مرور 1234) لإلغاء Device Admin وAccessibility.
17. **حراسة تعارض الميكروفون (Mic Conflict Guard)**: إذا كان تطبيق آخر (واتساب، هاتف...) يستخدم الميك حالياً، يُتخطى التسجيل لمنع التشويش.
18. **تسجيل 2 دقيقة كاملة**: استخدام bypassVad = true في حلقات التسجيل التلقائي لضمان مدة التسجيل الكاملة دون اقتطاع بسبب الصمت.

---

## 🛡️ بروتوكول منع كسر الميزات القديمة (Strict Anti-Regression Protocol)

يجب الالتزام بالخطوات التالية بشكل إلزامي وصارم في كل تعديل:

1. **المراجعة القبلية (Pre-Check Protocol):**
   - قبل تعديل أي كود في `app/` أو `MyMonitorService/` أو `fleet_logic.js`، قراءة `.agents/AGENTS.md` و `PROJECT_STATUS.md` لاستحضار شجرة الميزات بالكامل والتأكد من عدم المساس بوظائف قائمة.

2. **منع الآثار الجانبية (Isolation & Protection):**
   - عزل التعديلات الجديدة تماماً بحيث لا تؤثر على الدوال والآليات التي تم تثبيتها مسبقاً (البث المباشر، التكرار التلقائي، استقبال الأحداث، ضغط الصوت، حراسة تعارض المايك).

3. **الفحص والتحقق بعد التعديل (Post-Edit Audit):**
   - بعد تنفيذ أي تعديل، فحص ومراجعة الوظائف المجاورة (التسجيلات، الصور، المكالمات، استماع الويندوز، أزرار التحديد والتشغيل المتتالي) للتأكد من أنها لم تُحذف أو تُعطّل.

4. **التوثيق الفوري:**
   - تحديث `PROJECT_STATUS.md` فوراً بتسجيل الميزة الجديدة مع وضع علامة `[x]` تؤكد سلامة وبقاء الميزات السابقة المعتمدة.

