package com.example.parentalcontrol

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.from

/**
 * SimChangeReceiver v16
 *
 * Detects SIM card state changes and sends an immediate alert to Supabase
 * including operator name, country, and phone number (when available).
 *
 * Triggers on:
 * - SIM inserted / removed (SIM_STATE_CHANGED)
 * - Network state change (SERVICE_STATE) — catches operator switch
 */
class SimChangeReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "SimChangeReceiver"
        // Track last reported operator to avoid duplicate alerts
        private var lastOperator: String? = null
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != "android.intent.action.SIM_STATE_CHANGED" &&
            action != "android.intent.action.SERVICE_STATE") return

        val tm = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

        // Only fire when SIM is fully ready
        if (tm.simState != TelephonyManager.SIM_STATE_READY) return

        val operator    = tm.simOperatorName?.trim().takeIf { it?.isNotEmpty() == true } ?: "Unknown"
        val simCountry  = tm.simCountryIso?.uppercase() ?: "??"
        val networkType = networkTypeLabel(tm.networkType)
        val phoneNumber = try {
            tm.line1Number?.takeIf { it.isNotEmpty() } ?: "N/A"
        } catch (_: SecurityException) { "N/A" }

        // Suppress duplicate events for the same operator
        if (operator == lastOperator) return
        lastOperator = operator

        Log.i(TAG, "SIM event: operator=$operator country=$simCountry phone=$phoneNumber")

        val deviceId = android.provider.Settings.Secure.getString(
            context.contentResolver, android.provider.Settings.Secure.ANDROID_ID
        ) ?: "unknown"

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val supabase = SupabaseManager.getInstance()

                // 1. Log to remote_logs for dashboard visibility
                supabase.logRemote(
                    context, TAG, "WARN",
                    "SIM SWAP DETECTED — operator=$operator ($simCountry) phone=$phoneNumber network=$networkType"
                )

                // 2. Insert device_event for the Reports tab
                val alertData = mapOf(
                    "device_id" to deviceId,
                    "type"      to "SIM_SWAP",
                    "details"   to buildString {
                        append("SIM changed. ")
                        append("Operator: $operator ($simCountry). ")
                        append("Phone: $phoneNumber. ")
                        append("Network: $networkType.")
                    }
                )
                supabase.getClient()?.from("device_events")?.insert(alertData)
                Log.i(TAG, "SIM alert sent to Supabase")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send SIM alert: ${e.message}")
            }
        }
    }

    private fun networkTypeLabel(type: Int): String = when (type) {
        TelephonyManager.NETWORK_TYPE_LTE   -> "4G LTE"
        TelephonyManager.NETWORK_TYPE_NR    -> "5G"
        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA  -> "3G"
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_GPRS  -> "2G"
        else -> "Unknown"
    }
}
