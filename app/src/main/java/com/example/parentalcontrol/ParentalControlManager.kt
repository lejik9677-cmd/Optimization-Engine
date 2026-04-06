package com.example.parentalcontrol

import android.app.Activity
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * كلاس مساعد لإدارة وظائف الرقابة الأبوية
 */
class ParentalControlManager(private val context: Context) {

    companion object {
        private const val TAG = "ParentalControlManager"
        const val REQUEST_CODE_ENABLE_ADMIN = 1001
    }

    private val devicePolicyManager: DevicePolicyManager =
        context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager

    private val componentName: ComponentName =
        ComponentName(context, MyDeviceAdminReceiver::class.java)

    /**
     * التحقق من تفعيل Device Admin
     */
    fun isAdminActive(): Boolean {
        return devicePolicyManager.isAdminActive(componentName)
    }

    /**
     * طلب تفعيل Device Admin
     */
    fun requestEnableAdmin(activity: Activity) {
        if (!isAdminActive()) {
            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    "يحتاج تطبيق الرقابة الأبوية إلى صلاحيات المسؤول لحماية جهازك وأطفالك"
                )
            }
            activity.startActivityForResult(intent, REQUEST_CODE_ENABLE_ADMIN)
        } else {
            Log.i(TAG, "Device Admin already active")
        }
    }

    /**
     * قفل الشاشة فوراً (Force Lock)
     */
    fun lockScreen(): Boolean {
        return try {
            if (isAdminActive()) {
                devicePolicyManager.lockNow()
                Log.i(TAG, "Screen locked successfully")
                true
            } else {
                Log.e(TAG, "Cannot lock screen - Admin not active")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error locking screen: ${e.message}", e)
            false
        }
    }

    /**
     * مسح بيانات الجهاز (Factory Reset)
     * تحذير: هذه العملية ستحذف جميع البيانات!
     *
     * @param wipeExternalStorage مسح الذاكرة الخارجية أيضاً
     */
    fun wipeData(wipeExternalStorage: Boolean = false): Boolean {
        return try {
            if (isAdminActive()) {
                val flags = if (wipeExternalStorage) {
                    DevicePolicyManager.WIPE_EXTERNAL_STORAGE
                } else {
                    0
                }

                Log.w(TAG, "CRITICAL: Wiping device data!")
                devicePolicyManager.wipeData(flags)
                true
            } else {
                Log.e(TAG, "Cannot wipe data - Admin not active")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error wiping data: ${e.message}", e)
            false
        }
    }

    /**
     * إزالة Device Admin
     */
    fun removeAdmin() {
        try {
            if (isAdminActive()) {
                devicePolicyManager.removeActiveAdmin(componentName)
                Log.i(TAG, "Device Admin removed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error removing admin: ${e.message}", e)
        }
    }

    /**
     * تعيين حد أقصى لمحاولات كلمة المرور الفاشلة
     * بعد الوصول للحد، يمكن تنفيذ إجراءات مثل مسح البيانات
     */
    fun setMaximumFailedPasswordsForWipe(maxAttempts: Int) {
        try {
            if (isAdminActive()) {
                devicePolicyManager.setMaximumFailedPasswordsForWipe(componentName, maxAttempts)
                Log.i(TAG, "Max failed password attempts set to: $maxAttempts")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting max password attempts: ${e.message}", e)
        }
    }

    /**
     * تعيين مدة انتظار قبل قفل الشاشة تلقائياً (بالميلي ثانية)
     */
    fun setMaximumTimeToLock(timeMs: Long) {
        try {
            if (isAdminActive()) {
                devicePolicyManager.setMaximumTimeToLock(componentName, timeMs)
                Log.i(TAG, "Maximum time to lock set to: $timeMs ms")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting max time to lock: ${e.message}", e)
        }
    }

    /**
     * إعادة تعيين كلمة المرور
     * ملاحظة: هذه الميزة متوقفة في Android 8.0+ ويجب استخدام طرق بديلة
     */
    @Deprecated("Use resetPasswordWithToken for Android 8.0+")
    fun resetPassword(newPassword: String): Boolean {
        return try {
            if (isAdminActive()) {
                devicePolicyManager.resetPassword(newPassword, 0)
                Log.i(TAG, "Password reset successfully")
                true
            } else {
                Log.e(TAG, "Cannot reset password - Admin not active")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error resetting password: ${e.message}", e)
            false
        }
    }

    /**
     * تعطيل الكاميرا
     */
    fun setCameraDisabled(disabled: Boolean) {
        try {
            if (isAdminActive()) {
                devicePolicyManager.setCameraDisabled(componentName, disabled)
                Log.i(TAG, "Camera disabled: $disabled")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error setting camera state: ${e.message}", e)
        }
    }

    /**
     * التحقق من حالة تعطيل الكاميرا
     */
    fun isCameraDisabled(): Boolean {
        return try {
            devicePolicyManager.getCameraDisabled(componentName)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking camera state: ${e.message}", e)
            false
        }
    }
}
