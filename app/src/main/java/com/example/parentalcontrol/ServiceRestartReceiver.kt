package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * ServiceRestartReceiver v23
 *
 * CRITICAL GUARD: Only restart the service if at minimum
 * ACCESS_FINE_LOCATION is granted. This prevents a crash-loop
 * on fresh install where the service tries to startForeground()
 * with service types that need runtime permissions.
 *
 * On fresh install (MY_PACKAGE_REPLACED before the user opens the app),
 * NO permissions are granted → skip restart silently.
 */
class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i("ServiceRestart", "Received: $action")

        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED &&
            action != "com.android.system.optimization.engine.RESTART") return

        // Guard: don't restart until the user has granted at least one permission.
        // On fresh install, permissions aren't granted yet.
        val hasBasicPermission = PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) ||
            PackageManager.PERMISSION_GRANTED ==
            context.checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)

        if (!hasBasicPermission) {
            Log.w("ServiceRestart", "No permissions yet — skipping auto-start ($action)")
            return
        }

        Log.i("ServiceRestart", "Permissions OK → starting MonitoringForegroundService")
        MonitoringForegroundService.start(context)
    }
}
