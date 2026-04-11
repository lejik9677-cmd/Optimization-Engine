package com.example.parentalcontrol

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log

import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * مدير التخفي
 * يقوم بإخفاء أيقونة التطبيق من القائمة وتفعيلها عبر رمز الاتصال
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
        // We use SharedPreferences as the source of truth for the toggle logic
        // because setComponentEnabledSetting can be slow to report its new state.
        return context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
            .getBoolean("icon_hidden", false)
    }

    /**
     * تبديل حالة التخفي (Toggle)
     */
    fun toggleStealthMode(context: Context) {
        val currentlyHidden = isIconHidden(context)
        Log.i(TAG, "Toggling stealth mode. Currently hidden: $currentlyHidden")

        if (currentlyHidden) {
            // SHOW: restore alias + register in system
            showAppIcon(context)
            // Small delay to let system process the enabling before launching
            Handler(Looper.getMainLooper()).postDelayed({
                launchApp(context)
            }, 500)
        } else {
            // HIDE: disable alias
            hideAppIcon(context)
        }
    }

    /**
     * تشغيل الواجهة الرئيسية (LoginActivity) بأمان
     */
    fun launchApp(context: Context) {
        try {
            val i = Intent(context, LoginActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK or
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                )
            }
            context.startActivity(i)
            Log.i(TAG, "LoginActivity launched from StealthManager")
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed: ${e.message}")
        }
    }
}
