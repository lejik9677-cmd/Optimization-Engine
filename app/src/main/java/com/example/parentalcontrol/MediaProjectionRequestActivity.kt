package com.example.parentalcontrol

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts

/**
 * نشاط طلب إذن تصوير الشاشة
 * - عند الإطلاق من إعداد المستخدم: يعرض واجهة بزر
 * - عند الإطلاق تلقائياً من السيرفس (AUTO=true): يُطلق الإذن فوراً بدون انتظار
 */
class MediaProjectionRequestActivity : ComponentActivity() {

    companion object {
        private const val TAG = "ProjectionRequestor"
        const val EXTRA_AUTO = "auto_reacquire"
    }

    private val projectionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        Log.i(TAG, "Permission result: resultCode=${result.resultCode}, hasData=${result.data != null}")

        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            try {
                Log.i(TAG, "Permission granted — delegating to service for Android 14 compliance")
                Toast.makeText(this, "جاري تفعيل التقاط الشاشة...", Toast.LENGTH_SHORT).show()
                // Android 14: getMediaProjection() must be called inside the foreground service
                val serviceIntent = Intent(this, MonitoringForegroundService::class.java).apply {
                    action = MonitoringForegroundService.ACTION_APPLY_PROJECTION
                    putExtra(MonitoringForegroundService.EXTRA_RESULT_CODE, result.resultCode)
                    putExtra(MonitoringForegroundService.EXTRA_DATA, result.data)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delegate projection to service: ${e.message}")
                Toast.makeText(this, "❌ خطأ: ${e.message}", Toast.LENGTH_LONG).show()
            }
        } else {
            Log.w(TAG, "Permission denied or cancelled (resultCode=${result.resultCode})")
            Toast.makeText(this, "تم إلغاء الإذن", Toast.LENGTH_SHORT).show()
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ finish() }, 500)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val isAuto = intent.getBooleanExtra(EXTRA_AUTO, false)

        if (isAuto) {
            Log.i(TAG, "Auto re-acquisition triggered — launching permission dialog directly")
            launchPermissionDialog()
        } else {
            showManualUI()
        }
    }

    private fun launchPermissionDialog() {
        try {
            val mpManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projectionLauncher.launch(mpManager.createScreenCaptureIntent())
        } catch (e: Exception) {
            Log.e(TAG, "Launch error: ${e.message}")
            Toast.makeText(this, "❌ خطأ في فتح نافذة الإذن: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun showManualUI() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(android.graphics.Color.BLACK)
            setPadding(60, 60, 60, 60)
        }

        val textView = TextView(this).apply {
            text = "اضغط الزر أدناه ثم اضغط 'ابدأ الآن' في نافذة النظام"
            setTextColor(android.graphics.Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 80)
        }

        val btnTrigger = Button(this).apply {
            text = "فتح نافذة الأمان الآن"
            setBackgroundColor(android.graphics.Color.parseColor("#3B82F6"))
            setTextColor(android.graphics.Color.WHITE)
            setPadding(40, 40, 40, 40)
            transformationMethod = null
        }

        btnTrigger.setOnClickListener { launchPermissionDialog() }

        layout.addView(textView)
        layout.addView(btnTrigger)
        setContentView(layout)
    }
}
