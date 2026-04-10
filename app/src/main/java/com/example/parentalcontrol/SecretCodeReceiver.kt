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
            val code = intent.data?.host
            Log.i("SecretCodeReceiver", "Secret code dialed: $code")
            
            // 1. استعادة إظهار أيقونة التطبيق (في حالة الحاجة)
            StealthManager.showAppIcon(context)
            
            // 2. فتح صفحة الحماية (Login) للمتابعة
            val i = Intent(context, LoginActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            context.startActivity(i)
        }
    }
}
