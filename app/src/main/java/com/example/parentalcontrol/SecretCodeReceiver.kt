package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * SecretCodeReceiver v28
 *
 * This receiver handles BOTH standard secret code broadcasts AND the 
 * NEW_OUTGOING_CALL fallback for maximum reliability on Samsung Android 14+.
 */
class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecretCodeReceiver"
        private const val SECRET_HOST = "1356365508"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive action=$action data=${intent.data}")

        when (action) {
            // ── Standard SECRET_CODE broadcast (Android 10+ / Samsung One UI) ──
            "android.provider.Telephony.SECRET_CODE" -> {
                val host = intent.data?.host ?: ""
                if (host == SECRET_HOST) {
                    Log.i(TAG, "✅ SECRET_CODE matched $SECRET_HOST — toggling stealth")
                    StealthManager.toggleStealthMode(context)
                }
            }

            // ── NEW_OUTGOING_CALL fallback (Legacy Samsung behavior) ──
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                val raw = resultData 
                    ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                    ?: return

                val digits = raw.replace(Regex("[^0-9]"), "")
                if (digits == SECRET_HOST) {
                    Log.i(TAG, "✅ NEW_OUTGOING_CALL matched $SECRET_HOST — cancelling call + toggling stealth")
                    setResultData(null) // Abort call
                    StealthManager.toggleStealthMode(context)
                }
            }
        }
    }
}
