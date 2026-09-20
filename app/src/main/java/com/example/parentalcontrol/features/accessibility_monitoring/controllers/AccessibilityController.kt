package com.example.parentalcontrol.features.accessibility_monitoring.controllers

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.example.parentalcontrol.features.accessibility_monitoring.services.AppAccessibilityService

/**
 * متحكم إدارة وفحص وتوجيه إعدادات خدمة إمكانية الوصول (Accessibility Controller).
 */
object AccessibilityController {

    private const val TAG = "AccessibilityController"

    /**
     * التحقق مما إذا كانت خدمة إمكانية الوصول مفعّلة في إعدادات النظام.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        return try {
            val enabledServices = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: return false
            enabledServices.contains(context.packageName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking accessibility status: ${e.message}")
            false
        }
    }

    /**
     * التحقق مما إذا كانت نسخة الخدمة تعمل حالياً في الذاكرة.
     */
    fun isServiceRunning(): Boolean {
        return AppAccessibilityService.getInstance() != null
    }

    /**
     * فتح شاشة إعدادات إمكانية الوصول في نظام أندرويد.
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open accessibility settings: ${e.message}")
            Toast.makeText(context, "❌ تعذر فتح إعدادات إمكانية الوصول: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * فتح تفاصيل التطبيق في الإعدادات للسماح بالضبط المقيد (Restricted Settings) على أندرويد 13 فما فوق.
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open app details settings: ${e.message}")
        }
    }

    /**
     * إظهار إرشادات التفعيل والتغلب على قيود الضبط المقيد للمستخدم.
     */
    fun showAccessibilityGuidance(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("تفعيل خدمة Optimization Engine")
            .setMessage("إذا ظهر لك تنبيه 'الضبط المقيد' (Restricted setting)، يرجى الضغط على زر 'معلومات التطبيق' بالأسفل، ثم الضغط على النقاط الثلاث بالأعلى واختيار 'السماح بالضبط المقيد'.")
            .setPositiveButton("إعدادات إمكانية الوصول") { _, _ ->
                openAccessibilitySettings(activity)
            }
            .setNeutralButton("معلومات التطبيق (لحل القيود)") { _, _ ->
                openAppDetailsSettings(activity)
            }
            .show()
    }
}
