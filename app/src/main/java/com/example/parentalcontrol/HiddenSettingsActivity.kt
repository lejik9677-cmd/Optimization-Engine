package com.example.parentalcontrol

import android.app.AlertDialog
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.example.parentalcontrol.features.app_usage_and_notifications.services.AppNotificationListenerService
import com.example.parentalcontrol.features.app_usage_and_notifications.services.AppUsageTracker

/**
 * واجهة الإعدادات المخفية v2
 * تسمح للوالد بإدارة الخدمة، الأذونات، وإلغاء الصلاحيات الحساسة
 * محمية بكلمة مرور لمنع الوصول غير المصرح به
 */
class HiddenSettingsActivity : AppCompatActivity() {

    // كلمة المرور السرية — يمكن تغييرها هنا
    private val ADMIN_PASSWORD = "1234"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_hidden_settings)

        // ── تشغيل الخدمة ──────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            MonitoringForegroundService.start(this)
            Toast.makeText(this, "✅ بدأت خدمة المراقبة", Toast.LENGTH_SHORT).show()
        }

        // ── إيقاف الخدمة ──────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            MonitoringForegroundService.stop(this)
            Toast.makeText(this, "⛔ توقفت خدمة المراقبة", Toast.LENGTH_SHORT).show()
        }

        // ── إظهار الأيقونة ────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnShowIcon).setOnClickListener {
            StealthManager.showAppIcon(this)
            Toast.makeText(this, "👁 أيقونة التطبيق ظاهرة الآن", Toast.LENGTH_LONG).show()
        }

        // ── إدارة الأذونات ────────────────────────────────────────────────────
        findViewById<Button>(R.id.btnPermissions).setOnClickListener {
            checkAndRequestPermissions()
        }

        // ══════════════════════════════════════════════════════════════════════
        // ── أزرار إلغاء الصلاحيات (محمية بكلمة مرور) ─────────────────────────
        // ══════════════════════════════════════════════════════════════════════

        // 🔓 إلغاء Device Admin
        findViewById<Button>(R.id.btnRevokeDeviceAdmin).setOnClickListener {
            showPasswordDialog(
                title = "🔓 إلغاء صلاحية مدير الجهاز",
                message = "سيتم إلغاء صلاحية Device Admin.\nبعد ذلك يمكن حذف التطبيق بسهولة.\n\nأدخل كلمة المرور للتأكيد:"
            ) {
                revokeDeviceAdmin()
            }
        }

        // ♿ إلغاء Accessibility Service
        findViewById<Button>(R.id.btnRevokeAccessibility).setOnClickListener {
            showPasswordDialog(
                title = "♿ إلغاء إمكانية الوصول",
                message = "سيتم فتح إعدادات إمكانية الوصول.\nقم بإيقاف تشغيل الخدمة يدوياً.\n\nأدخل كلمة المرور للمتابعة:"
            ) {
                openAccessibilitySettings()
            }
        }

        // ⚙ فتح إعدادات التطبيق (إلغاء أي صلاحية)
        findViewById<Button>(R.id.btnOpenAppSettings).setOnClickListener {
            showPasswordDialog(
                title = "⚙ إعدادات التطبيق",
                message = "سيتم فتح إعدادات التطبيق في النظام.\nيمكنك إلغاء أي صلاحية من هناك.\n\nأدخل كلمة المرور للمتابعة:"
            ) {
                openAppSystemSettings()
            }
        }
    }

    // ── حوار كلمة المرور ──────────────────────────────────────────────────────

    private fun showPasswordDialog(title: String, message: String, onSuccess: () -> Unit) {
        val passwordInput = EditText(this).apply {
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                        android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "كلمة المرور"
            setPadding(40, 20, 40, 20)
        }

        AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setView(passwordInput)
            .setPositiveButton("✅ تأكيد") { dialog, _ ->
                val entered = passwordInput.text.toString()
                if (entered == ADMIN_PASSWORD) {
                    dialog.dismiss()
                    onSuccess()
                } else {
                    Toast.makeText(this, "❌ كلمة المرور غير صحيحة", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("إلغاء") { dialog, _ -> dialog.dismiss() }
            .show()
    }

    // ── إلغاء Device Admin ────────────────────────────────────────────────────

    private fun revokeDeviceAdmin() {
        try {
            val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
            val adminComponent = ComponentName(this, MyDeviceAdminReceiver::class.java)

            if (dpm.isAdminActive(adminComponent)) {
                dpm.removeActiveAdmin(adminComponent)
                Toast.makeText(
                    this,
                    "✅ تم إلغاء صلاحية Device Admin — يمكنك الآن حذف التطبيق",
                    Toast.LENGTH_LONG
                ).show()

                // Log the operation to Supabase
                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        SupabaseManager.getInstance().logRemote(
                            this@HiddenSettingsActivity,
                            "HiddenSettings",
                            "INFO",
                            "🔓 Device Admin revoked manually by parent from HiddenSettingsActivity"
                        )
                    } catch (_: Exception) {}
                }
            } else {
                Toast.makeText(
                    this,
                    "ℹ️ صلاحية Device Admin غير مفعلة أصلاً",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "❌ فشل إلغاء الصلاحية: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ── فتح إعدادات Accessibility ─────────────────────────────────────────────

    private fun openAccessibilitySettings() {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
            Toast.makeText(
                this,
                "ℹ️ ابحث عن 'Resource Engine' وأوقف تشغيله",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ تعذر فتح الإعدادات: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── فتح إعدادات النظام للتطبيق ────────────────────────────────────────────

    private fun openAppSystemSettings() {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(intent)
            Toast.makeText(
                this,
                "ℹ️ يمكنك إلغاء أي صلاحية من هذه الصفحة",
                Toast.LENGTH_LONG
            ).show()
        } catch (e: Exception) {
            Toast.makeText(this, "❌ تعذر فتح الإعدادات: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // ── فحص الأذونات الأساسية ─────────────────────────────────────────────────

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

        // 3. صلاحية تتبع استخدام التطبيقات
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

        Toast.makeText(this, "✅ جميع الأذونات الأساسية مفعلة", Toast.LENGTH_SHORT).show()
    }
}
