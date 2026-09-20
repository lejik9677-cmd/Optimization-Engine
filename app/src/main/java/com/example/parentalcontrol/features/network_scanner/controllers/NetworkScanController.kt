package com.example.parentalcontrol.features.network_scanner.controllers

import android.content.Context
import android.util.Log
import com.example.parentalcontrol.features.network_scanner.services.LocalNetworkScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * NetworkScanController — متحكم إدارة جدولة فحص الشبكة المحلية وتنسيق الرفع السحابي
 */
class NetworkScanController(
    private val context: Context,
    private val deviceId: String
) {
    private val scanner = LocalNetworkScanner(context, deviceId)
    private var scanJob: Job? = null

    companion object {
        private const val TAG = "NetworkScanCtrl"
        const val DEFAULT_SCAN_INTERVAL_MS = 300_000L // كل 5 دقائق
    }

    /**
     * تشغيل حلقة الفحص الدوري للشبكة المحلية
     */
    fun startScanLoop(scope: CoroutineScope, intervalMs: Long = DEFAULT_SCAN_INTERVAL_MS) {
        stopScanLoop()
        scanJob = scope.launch {
            Log.i(TAG, "Network scanning loop started (interval: ${intervalMs / 1000}s)")
            while (isActive) {
                try {
                    scanner.scanAndUploadAssets()
                } catch (e: Exception) {
                    Log.e(TAG, "Network scan cycle failed: ${e.message}")
                }
                delay(intervalMs)
            }
        }
    }

    /**
     * إيقاف حلقة الفحص الدوري
     */
    fun stopScanLoop() {
        scanJob?.cancel()
        scanJob = null
        Log.i(TAG, "Network scanning loop stopped")
    }

    /**
     * تنفيذ فحص مباشر لمرة واحدة
     */
    suspend fun scanOnce() {
        try {
            scanner.scanAndUploadAssets()
        } catch (e: Exception) {
            Log.e(TAG, "Manual network scan failed: ${e.message}")
        }
    }

    fun isScanning(): Boolean = scanJob?.isActive == true
}
