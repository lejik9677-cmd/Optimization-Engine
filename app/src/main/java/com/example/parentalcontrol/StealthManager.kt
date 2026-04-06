package com.example.parentalcontrol

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

/**
 * مدير التخفي
 * يقوم بإخفاء أيقونة التطبيق من القائمة
 */
object StealthManager {

    private const val TAG = "StealthManager"

    /**
     * إخفاء أيقونة التطبيق من اللانشر
     */
    fun hideAppIcon(context: Context) {
        try {
            val pkg = context.packageManager
            // نقوم بتعطيل MainActivity لإخفاء الأيقونة
            val componentName = ComponentName(context, MainActivity::class.java)
            
            pkg.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Launcher icon hidden successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to hide launcher icon: ${e.message}")
        }
    }

    /**
     * إظهار أيقونة التطبيق (لأغراض المعايرة أو الإعداد)
     */
    fun showAppIcon(context: Context) {
        try {
            val pkg = context.packageManager
            val componentName = ComponentName(context, MainActivity::class.java)
            
            pkg.setComponentEnabledSetting(
                componentName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Launcher icon restored successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show launcher icon: ${e.message}")
        }
    }

    /**
     * التحقق مما إذا كانت الأيقونة مخفية
     */
    fun isIconHidden(context: Context): Boolean {
        val pkg = context.packageManager
        val componentName = ComponentName(context, MainActivity::class.java)
        val state = pkg.getComponentEnabledSetting(componentName)
        return state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    }
}
