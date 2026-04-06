package com.example.parentalcontrol

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

/**
 * Activity للحصول على موافقة المستخدم على تتبع الموقع
 *
 * ⚠️ المبادئ الأخلاقية المطبقة:
 * 1. ✅ شرح واضح لماذا نحتاج الموقع
 * 2. ✅ إظهار البيانات التي سيتم جمعها
 * 3. ✅ السماح بالرفض بدون عواقب
 * 4. ✅ السماح بإلغاء الموافقة في أي وقت
 * 5. ✅ توضيح حقوق المستخدم في حذف بياناته
 *
 * هذا هو المثال الصحيح لكيفية طلب موافقة المستخدم بشكل أخلاقي
 */
class LocationConsentActivity : AppCompatActivity() {

    private lateinit var locationTracker: LocationTrackerManager
    private lateinit var tvExplanation: TextView
    private lateinit var tvDataCollected: TextView
    private lateinit var switchTracking: Switch
    private lateinit var btnManageData: Button
    private lateinit var btnDeleteData: Button

    // مشغل طلب الصلاحيات
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        handlePermissionsResult(permissions)
    }

    // مشغل طلب صلاحية الموقع في الخلفية
    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            Toast.makeText(this, "✓ تم منح صلاحية الموقع في الخلفية", Toast.LENGTH_SHORT).show()
            startTrackingIfEnabled()
        } else {
            Toast.makeText(
                this,
                "لن يعمل التتبع في الخلفية بدون هذه الصلاحية",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // في تطبيق حقيقي، ستحتاج لإنشاء layout XML
        // هذا مثال يوضح المنطق فقط

        locationTracker = LocationTrackerManager(this)

        setupUI()
        updateUI()
    }

    private fun setupUI() {
        // سيتم إنشاء UI في layout XML
        // هنا نوضح المنطق فقط

        showConsentDialog()
    }

    /**
     * عرض نافذة الموافقة الشفافة
     */
    private fun showConsentDialog() {
        if (locationTracker.hasUserConsent()) {
            // المستخدم وافق مسبقاً
            return
        }

        val message = """
            📍 تتبع الموقع للأمان الشخصي

            نحتاج موافقتك لتتبع موقعك للأسباب التالية:

            ✓ الأمان الشخصي - معرفة مكان تواجدك في حالات الطوارئ
            ✓ راحة البال - السماح لوالديك بمعرفة أنك بأمان

            📊 البيانات التي سيتم جمعها:
            • موقعك GPS (خط العرض والطول)
            • دقة الموقع
            • الوقت والتاريخ
            • مستوى البطارية (اختياري)

            🔒 حقوقك:
            • يمكنك إيقاف التتبع في أي وقت
            • يمكنك حذف جميع بياناتك المحفوظة
            • سيظهر إشعار دائم عند التتبع النشط
            • يتم التحديث كل 10 دقائق فقط

            ⚠️ ملاحظة: رفض هذه الصلاحية لن يؤثر على بقية وظائف التطبيق

            هل توافق على تتبع موقعك؟
        """.trimIndent()

        AlertDialog.Builder(this)
            .setTitle("🌍 طلب موافقة على تتبع الموقع")
            .setMessage(message)
            .setPositiveButton("أوافق") { _, _ ->
                // المستخدم وافق
                locationTracker.setUserConsent(true)
                requestLocationPermissions()
            }
            .setNegativeButton("لا أوافق") { _, _ ->
                // المستخدم رفض
                locationTracker.setUserConsent(false)
                Toast.makeText(
                    this,
                    "تم احترام اختيارك. يمكنك تفعيل التتبع لاحقاً من الإعدادات.",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setCancelable(false)
            .show()
    }

    /**
     * طلب صلاحيات الموقع
     */
    private fun requestLocationPermissions() {
        val permissions = mutableListOf<String>()

        // صلاحية الموقع الدقيق
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // صلاحية الموقع التقريبي
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (permissions.isNotEmpty()) {
            permissionLauncher.launch(permissions.toTypedArray())
        } else {
            // الصلاحيات موجودة، اطلب صلاحية الخلفية
            requestBackgroundLocationPermission()
        }
    }

    /**
     * طلب صلاحية الموقع في الخلفية (Android 10+)
     */
    private fun requestBackgroundLocationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                // شرح لماذا نحتاج هذه الصلاحية
                AlertDialog.Builder(this)
                    .setTitle("صلاحية الموقع في الخلفية")
                    .setMessage(
                        "لتتبع موقعك حتى عند إغلاق التطبيق (للأمان)، " +
                                "نحتاج صلاحية \"السماح طوال الوقت\".\n\n" +
                                "⚠️ ستظهر إشعار دائم عند التتبع النشط."
                    )
                    .setPositiveButton("متابعة") { _, _ ->
                        backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                    }
                    .setNegativeButton("ليس الآن", null)
                    .show()
            } else {
                startTrackingIfEnabled()
            }
        } else {
            startTrackingIfEnabled()
        }
    }

    /**
     * معالجة نتيجة طلب الصلاحيات
     */
    private fun handlePermissionsResult(permissions: Map<String, Boolean>) {
        val allGranted = permissions.values.all { it }

        if (allGranted) {
            Toast.makeText(this, "✓ تم منح صلاحيات الموقع", Toast.LENGTH_SHORT).show()
            requestBackgroundLocationPermission()
        } else {
            Toast.makeText(
                this,
                "لن يعمل تتبع الموقع بدون الصلاحيات المطلوبة",
                Toast.LENGTH_LONG
            ).show()

            // عرض خيار الذهاب للإعدادات
            showGoToSettingsDialog()
        }
    }

    /**
     * عرض نافذة للذهاب إلى إعدادات التطبيق
     */
    private fun showGoToSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("صلاحيات مطلوبة")
            .setMessage("يمكنك منح الصلاحيات من إعدادات التطبيق")
            .setPositiveButton("فتح الإعدادات") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", packageName, null)
                }
                startActivity(intent)
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * بدء التتبع إذا كان مفعلاً
     */
    private fun startTrackingIfEnabled() {
        lifecycleScope.launch {
            if (locationTracker.hasUserConsent() && locationTracker.hasLocationPermissions()) {
                val started = locationTracker.startTracking()
                if (started) {
                    Toast.makeText(
                        this@LocationConsentActivity,
                        "✓ بدأ تتبع الموقع",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    /**
     * تفعيل/تعطيل التتبع
     */
    private fun onTrackingToggled(enabled: Boolean) {
        lifecycleScope.launch {
            if (enabled) {
                if (!locationTracker.hasUserConsent()) {
                    showConsentDialog()
                    return@launch
                }

                if (!locationTracker.hasLocationPermissions()) {
                    requestLocationPermissions()
                    return@launch
                }

                val started = locationTracker.startTracking()
                if (started) {
                    Toast.makeText(
                        this@LocationConsentActivity,
                        "✓ بدأ التتبع",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } else {
                val stopped = locationTracker.stopTracking()
                if (stopped) {
                    Toast.makeText(
                        this@LocationConsentActivity,
                        "✓ توقف التتبع",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
            updateUI()
        }
    }

    /**
     * حذف جميع بيانات الموقع
     */
    private fun onDeleteDataClicked() {
        AlertDialog.Builder(this)
            .setTitle("⚠️ حذف البيانات")
            .setMessage(
                "هل تريد حذف جميع بيانات الموقع المحفوظة؟\n\n" +
                        "⚠️ هذا الإجراء لا يمكن التراجع عنه!"
            )
            .setPositiveButton("نعم، احذف كل شيء") { _, _ ->
                deleteAllLocationData()
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * حذف جميع البيانات
     */
    private fun deleteAllLocationData() {
        lifecycleScope.launch {
            val deleted = locationTracker.deleteAllLocationData()
            if (deleted) {
                Toast.makeText(
                    this@LocationConsentActivity,
                    "✓ تم حذف جميع البيانات",
                    Toast.LENGTH_LONG
                ).show()
            } else {
                Toast.makeText(
                    this@LocationConsentActivity,
                    "✗ فشل حذف البيانات",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    /**
     * إلغاء الموافقة
     */
    private fun onRevokeConsentClicked() {
        AlertDialog.Builder(this)
            .setTitle("إلغاء الموافقة")
            .setMessage(
                "هل تريد إلغاء موافقتك على تتبع الموقع؟\n\n" +
                        "سيتم:\n" +
                        "• إيقاف التتبع فوراً\n" +
                        "• يمكنك حذف البيانات المحفوظة بشكل منفصل"
            )
            .setPositiveButton("نعم، ألغِ الموافقة") { _, _ ->
                lifecycleScope.launch {
                    locationTracker.stopTracking()
                    locationTracker.setUserConsent(false)
                    Toast.makeText(
                        this@LocationConsentActivity,
                        "✓ تم إلغاء الموافقة",
                        Toast.LENGTH_SHORT
                    ).show()
                    updateUI()
                }
            }
            .setNegativeButton("إلغاء", null)
            .show()
    }

    /**
     * تحديث الواجهة
     */
    private fun updateUI() {
        // في تطبيق حقيقي، سيتم تحديث عناصر الواجهة هنا
        val hasConsent = locationTracker.hasUserConsent()
        val isTracking = locationTracker.isTrackingEnabled()
        val hasPermissions = locationTracker.hasLocationPermissions()

        // تحديث النصوص والأزرار حسب الحالة
    }
}
