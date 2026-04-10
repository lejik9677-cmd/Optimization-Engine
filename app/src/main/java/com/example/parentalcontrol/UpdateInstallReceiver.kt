package com.example.parentalcontrol

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * مستقبل نتائج التثبيت من PackageInstaller
 * يعالج: نجاح التثبيت / طلب تأكيد المستخدم / فشل التثبيت
 */
class UpdateInstallReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_INSTALL_RESULT = "com.example.parentalcontrol.UPDATE_INSTALL_RESULT"
        private const val TAG = "UpdateInstallReceiver"
        private const val CHANNEL_ID = "update_install_channel"
        private const val NOTIF_ID = 5001
    }

    override fun onReceive(context: Context, intent: Intent) {
        val status  = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE) ?: "unknown"

        Log.i(TAG, "Install callback → status=$status, msg=$message")

        when (status) {
            PackageInstaller.STATUS_SUCCESS -> {
                Log.i(TAG, "Update installed successfully ✅")
                AppUpdateManager(context).clearPendingUpdate()
                cancelNotification(context)
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseManager.getInstance().logRemote(context, TAG, "INFO",
                        "Update installed successfully on device")
                }
            }

            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Android 10+: لا يمكن startActivity من BroadcastReceiver في الخلفية مباشرةً
                // الحل: عرض إشعار → المستخدم يضغط عليه → يفتح نافذة التثبيت
                Log.i(TAG, "User action required → showing install notification")

                @Suppress("DEPRECATION")
                val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                } else {
                    intent.getParcelableExtra(Intent.EXTRA_INTENT)
                }

                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

                    // أولاً: حاول مباشرةً (يعمل إذا كان التطبيق في المقدمة)
                    try {
                        context.startActivity(confirmIntent)
                        Log.i(TAG, "Install dialog launched directly ✅")
                        return
                    } catch (e: Exception) {
                        Log.w(TAG, "Direct launch failed (background) → falling back to notification: ${e.message}")
                    }

                    // ثانياً: أظهر إشعاراً يفتح نافذة التثبيت عند الضغط
                    showInstallNotification(context, confirmIntent)
                } else {
                    Log.e(TAG, "confirmIntent is null — cannot proceed")
                }
            }

            PackageInstaller.STATUS_FAILURE,
            PackageInstaller.STATUS_FAILURE_ABORTED,
            PackageInstaller.STATUS_FAILURE_BLOCKED,
            PackageInstaller.STATUS_FAILURE_CONFLICT,
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE,
            PackageInstaller.STATUS_FAILURE_INVALID,
            PackageInstaller.STATUS_FAILURE_STORAGE -> {
                Log.e(TAG, "Install failed: status=$status → $message")
                CoroutineScope(Dispatchers.IO).launch {
                    SupabaseManager.getInstance().logRemote(context, TAG, "ERROR",
                        "Install failed [status=$status]: $message")
                }
            }
        }
    }

    private fun showInstallNotification(context: Context, confirmIntent: Intent) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "تحديثات التطبيق", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "إشعارات تثبيت تحديثات التطبيق"
                }
            )
        }

        val pendingIntent = PendingIntent.getActivity(
            context, NOTIF_ID, confirmIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("تحديث جاهز للتثبيت")
            .setContentText("اضغط هنا لإتمام تثبيت التحديث")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        nm.notify(NOTIF_ID, notif)
        Log.i(TAG, "Install notification shown — waiting for user tap")

        CoroutineScope(Dispatchers.IO).launch {
            SupabaseManager.getInstance().logRemote(context, TAG, "INFO",
                "Install notification shown — user tap required")
        }
    }

    private fun cancelNotification(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(NOTIF_ID)
    }
}
