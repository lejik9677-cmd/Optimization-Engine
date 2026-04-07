package com.example.parentalcontrol

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * خدمة إمكانية الوصول (Accessibility Service)
 * الميزة الأقوى: كشف المكالمات النشطة في أندرويد 12 و13
 */
class AppAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isCallActive = false
    private lateinit var audioRecorder: AudioRecorderEngine

    override fun onCreate() {
        super.onCreate()
        audioRecorder = AudioRecorderEngine(this)
        Log.i(TAG, "Accessibility Service created")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // مراقبة تطبيقات الهاتف للتعرف على بداية ونهاية المكالمة
        val phonePackages = listOf("com.google.android.dialer", "com.android.server.telecom", "com.android.phone")
        
        if (phonePackages.contains(packageName)) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                    // كشف شاشة المكالمة (هذه الطريقة تختلف حسب واجهة الهاتف)
                    // إذا كان محتوى الشاشة يحتوي على كلمات مثل "Call" أو "End", سنقوم ببدء التسجيل
                    val text = event.text.toString()
                    if (isCallUI(text) && !isCallActive) {
                        startCallRecording()
                    } else if (isEndCallUI(text) && isCallActive) {
                        stopCallRecording()
                    }
                }
            }
        }
    }

    private fun isCallUI(text: String): Boolean {
        val keywords = listOf("Incoming", "Ongoing", "Call", "اتصال", "جاري")
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun isEndCallUI(text: String): Boolean {
        val keywords = listOf("Ended", "Finished", "انتهت", "مكالمة فائتة")
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun startCallRecording() {
        Log.i(TAG, "Call detected! Starting call recording module...")
        isCallActive = true
        audioRecorder.startRecording()
    }

    private fun stopCallRecording() {
        Log.i(TAG, "Call ended. Saving recording...")
        isCallActive = false
        audioRecorder.stopRecording(upload = true)
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    companion object {
        private const val TAG = "AppAccessibility"
    }
}
