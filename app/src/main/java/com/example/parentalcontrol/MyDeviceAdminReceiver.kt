package com.example.parentalcontrol

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast

/**
 * Device Admin Receiver للرقابة الأبوية (Parental Control)
 * يوفر صلاحيات قفل الجهاز ومسح البيانات
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {

    companion object {
        private const val TAG = "ParentalControlAdmin"
    }

    /**
     * يتم استدعاء هذه الدالة عندما يتم تفعيل Device Admin
     */
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Log.i(TAG, "Device Admin enabled for Parental Control")
        
        // التحقق مما إذا كان التطبيق هو Device Owner (يتطلب ADB أو DPC)
        val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
        val adminComponent = android.content.ComponentName(context, MyDeviceAdminReceiver::class.java)
        
        try {
            if (dpm.isDeviceOwnerApp(context.packageName)) {
                Log.i(TAG, "App is Device Owner! Blocking uninstallation...")
                dpm.setUninstallBlocked(adminComponent, context.packageName, true)
                Toast.makeText(context, "تم تفعيل حماية النظام القصوى", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to set uninstall blocked: ${e.message}")
        }

        Toast.makeText(
            context,
            "تم تفعيل الرقابة الأبوية",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * يتم استدعاء هذه الدالة عندما يحاول المستخدم تعطيل Device Admin
     */
    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        Log.w(TAG, "Device Admin disable requested")
        return "تحذير: تعطيل الرقابة الأبوية سيزيل الحماية المفعلة"
    }

    /**
     * يتم استدعاء هذه الدالة عندما يتم تعطيل Device Admin
     */
    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Log.i(TAG, "Device Admin disabled")
        Toast.makeText(
            context,
            "تم تعطيل الرقابة الأبوية",
            Toast.LENGTH_LONG
        ).show()
    }

    /**
     * يتم استدعاء هذه الدالة عندما يفشل المستخدم في إدخال كلمة المرور الصحيحة
     */
    override fun onPasswordFailed(context: Context, intent: Intent) {
        super.onPasswordFailed(context, intent)
        Log.w(TAG, "Password failed attempt detected")
        Toast.makeText(
            context,
            "محاولة إدخال كلمة مرور خاطئة",
            Toast.LENGTH_SHORT
        ).show()
    }

    /**
     * يتم استدعاء هذه الدالة عندما ينجح المستخدم في إدخال كلمة المرور
     */
    override fun onPasswordSucceeded(context: Context, intent: Intent) {
        super.onPasswordSucceeded(context, intent)
        Log.i(TAG, "Password succeeded")
    }

    /**
     * يتم استدعاء هذه الدالة عندما يتم تغيير كلمة المرور
     */
    override fun onPasswordChanged(context: Context, intent: Intent) {
        super.onPasswordChanged(context, intent)
        Log.i(TAG, "Password changed")
        Toast.makeText(
            context,
            "تم تغيير كلمة المرور",
            Toast.LENGTH_SHORT
        ).show()
    }
}
