package com.example.parentalcontrol.features.media_observer.controllers

import android.content.Context
import android.util.Log
import com.example.parentalcontrol.features.media_observer.services.MediaObserver

/**
 * MediaObserverController — متحكم إدارة تسجيل وإلغاء وتنسيق مراقبة ورفع الوسائط والصور
 */
class MediaObserverController(private val context: Context) {

    private val observer = MediaObserver(context)
    private var isStarted = false

    companion object {
        private const val TAG = "MediaObserverCtrl"
    }

    /**
     * تسجيل ومزامنة مراقبة الصور الجديدة
     */
    fun start() {
        if (isStarted) {
            Log.d(TAG, "MediaObserverController is already running")
            return
        }
        try {
            observer.start()
            isStarted = true
            Log.i(TAG, "MediaObserverController started — monitoring active")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start MediaObserverController: ${e.message}")
        }
    }

    /**
     * إلغاء تسجيل المراقب وإيقاف الرصد
     */
    fun stop() {
        if (!isStarted) {
            Log.d(TAG, "MediaObserverController is not running")
            return
        }
        try {
            observer.stop()
            isStarted = false
            Log.i(TAG, "MediaObserverController stopped successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop MediaObserverController: ${e.message}")
        }
    }

    /**
     * التحقق من حالة المراقبة
     */
    fun isRunning(): Boolean = isStarted
}
