package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log

/**
 * SecretCodeReceiver v18
 * ────────────────────────────────────────────────────────────────
 * Triggered by dialing  *#1356365508#  in any phone dialer.
 *
 * Behavior (toggle logic):
 *   • If app icon is HIDDEN → show it + open LoginActivity
 *   • If app icon is VISIBLE → hide it (stealth mode)
 *
 * Android 12+ / Samsung One UI notes:
 *   The system delivers SECRET_CODE broadcasts to explicit receivers only.
 *   We use a dual-action approach:
 *   1. android.provider.Telephony.SECRET_CODE  (standard)
 *   2. android.provider.Telephony.SECRET_CODE with NEW_OUTGOING_CALL fallback
 *
 *   On Samsung One UI 7, the stock dialer sends SECRET_CODE;
 *   third-party dialers may not — the user must use the stock Phone app.
 * ────────────────────────────────────────────────────────────────
 */
class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecretCodeReceiver"
        private const val SECRET_HOST = "1356365508"

        // SharedPreferences key to track icon visibility
        private const val PREFS_NAME = "stealth_prefs"
        private const val KEY_ICON_HIDDEN = "icon_hidden"

        fun isIconHidden(context: Context): Boolean =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getBoolean(KEY_ICON_HIDDEN, false)

        fun setIconHidden(context: Context, hidden: Boolean) =
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit().putBoolean(KEY_ICON_HIDDEN, hidden).apply()
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive action=$action data=${intent.data}")

        when (action) {
            // ── Standard SECRET_CODE broadcast (most Android / some Samsung) ──
            "android.provider.Telephony.SECRET_CODE" -> {
                val host = intent.data?.host ?: ""
                if (host != SECRET_HOST) {
                    Log.d(TAG, "Different secret code ($host) — ignoring")
                    return
                }
                Log.i(TAG, "✅ SECRET_CODE received — toggling stealth")
                toggleStealthMode(context)
            }

            // ── NEW_OUTGOING_CALL fallback (Samsung One UI 5/6/7 reliable path) ──
            // Intercepts the dial attempt BEFORE the call connects.
            // setResultData(null) cancels the outgoing call silently.
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                val raw = resultData                          // the number about to be dialed
                    ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                    ?: return
                // Strip everything except digits — handles *#1356365508# and variants
                val digits = raw.replace(Regex("[^0-9]"), "")
                if (digits != SECRET_HOST) return

                Log.i(TAG, "✅ NEW_OUTGOING_CALL matched $SECRET_HOST — cancelling call + toggling stealth")
                setResultData(null)          // abort the outgoing call
                toggleStealthMode(context)
            }

            else -> return
        }
    }

    // ── Toggle logic ───────────────────────────────────────────────────────────

    private fun toggleStealthMode(context: Context) {
        val currentlyHidden = isIconHidden(context)

        if (currentlyHidden) {
            // SHOW: restore alias + open login
            Log.i(TAG, "Icon was hidden → showing + launching LoginActivity")
            showAlias(context)
            setIconHidden(context, false)

            // Small delay to let PackageManager apply alias change
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                launchApp(context)
            }, 400)
        } else {
            // HIDE: disable alias
            Log.i(TAG, "Icon was visible → hiding (stealth mode)")
            hideAlias(context)
            setIconHidden(context, true)
        }
    }

    // ── Icon alias management ──────────────────────────────────────────────────

    private fun hideAlias(context: Context) {
        setComponentEnabled(context, ".LauncherAlias", PackageManager.COMPONENT_ENABLED_STATE_DISABLED)
        Log.i(TAG, "LauncherAlias DISABLED — icon hidden")
    }

    private fun showAlias(context: Context) {
        setComponentEnabled(context, ".LauncherAlias", PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        Log.i(TAG, "LauncherAlias ENABLED — icon visible")
    }

    private fun setComponentEnabled(context: Context, shortName: String, state: Int) {
        try {
            val component = ComponentName(context.packageName, context.packageName + shortName)
            context.packageManager.setComponentEnabledSetting(
                component, state,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            Log.e(TAG, "setComponentEnabled($shortName) failed: ${e.message}")
        }
    }

    // ── Launch app ─────────────────────────────────────────────────────────────

    private fun launchApp(context: Context) {
        try {
            val i = Intent(context, LoginActivity::class.java).apply {
                addFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK       or
                    Intent.FLAG_ACTIVITY_CLEAR_TASK     or
                    Intent.FLAG_ACTIVITY_BROUGHT_TO_FRONT
                )
            }
            context.startActivity(i)
            Log.i(TAG, "LoginActivity launched")
        } catch (e: Exception) {
            Log.e(TAG, "launchApp failed: ${e.message}")
        }
    }
}
