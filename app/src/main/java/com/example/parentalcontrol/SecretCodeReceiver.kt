package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * مستقبل الرمز السري (*#199018#)
 * يسمح بإظهار أيقونة التطبيق وفتح الإعدادات حتى لو كانت مخفية
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SECRET_CODE") {
            Log.i("SecretCodeReceiver", "Secret code dialed: 199018")
            
            // 1. استعادة إظهار أيقونة التطبيق
            StealthManager.showAppIcon(context)
            
            // 2. فتح النشاط المخفي للإعدادات
            val i = Intent(context, HiddenSettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            context.startActivity(i)
        }
    }
}
