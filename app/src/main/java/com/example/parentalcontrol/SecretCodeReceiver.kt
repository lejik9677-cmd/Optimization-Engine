package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * مستقبل الرمز السري (*#7777#)
 * يسمح بفتح إعدادات التطبيق حتى لو كانت الأيقونة مخفية
 */
class SecretCodeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SECRET_CODE") {
            Log.i("SecretCodeReceiver", "Secret code dialed!")
            
            // فتح النشاط المخفي
            val i = Intent(context, HiddenSettingsActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(i)
        }
    }
}
