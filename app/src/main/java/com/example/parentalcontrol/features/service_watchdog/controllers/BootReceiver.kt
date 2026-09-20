package com.example.parentalcontrol.features.service_watchdog.controllers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * مستقبل إشارات إقلاع النظام وتحديث الحزمة (Boot Receiver)
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "Boot/Package action received: $action")

        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            ServiceWatchdogController.startService(context)
            ServiceWatchdogController.scheduleWatchdogs(context)
        }
    }
}
