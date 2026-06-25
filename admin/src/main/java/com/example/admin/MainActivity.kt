package com.example.admin

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.webkit.*
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var loadingOverlay: View

    companion object {
        private const val DASHBOARD_URL = "https://optimization-engine-238a4.web.app/"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // ── شاشة كاملة بدون شريط العنوان ──────────────────────────
        supportActionBar?.hide()
        window.apply {
            addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            statusBarColor = Color.parseColor("#0f172a")
            navigationBarColor = Color.parseColor("#0f172a")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                setDecorFitsSystemWindows(true)
            }
        }

        // ── Root Layout ───────────────────────────────────────────
        val rootLayout = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#020617"))
        }
        setContentView(rootLayout)

        // ── WebView ───────────────────────────────────────────────
        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#020617"))
        }
        rootLayout.addView(webView)

        // ── Loading Overlay ───────────────────────────────────────
        loadingOverlay = buildLoadingView()
        rootLayout.addView(loadingOverlay)

        // ── WebView Settings ──────────────────────────────────────
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            mediaPlaybackRequiresUserGesture = false
            // LOAD_DEFAULT: يستخدم cache للجلسة لكن يُحدّث عند تغيّر المحتوى
            cacheMode = WebSettings.LOAD_DEFAULT
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(true)
            builtInZoomControls = false
            displayZoomControls = false
            // السماح بالملفات والمحتوى المختلط
            allowFileAccess = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            // User Agent مخصص يظهر كمتصفح حديث
            userAgentString = "Mozilla/5.0 (Linux; Android 13; AdminConsole) " +
                "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        }

        // ── WebViewClient ─────────────────────────────────────────
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                loadingOverlay.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                // إخفاء overlay بتأخير بسيط للسماح لـ JS بالإقلاع
                webView.postDelayed({
                    loadingOverlay.animate()
                        .alpha(0f)
                        .setDuration(400)
                        .withEndAction { loadingOverlay.visibility = View.GONE }
                        .start()
                }, 600)
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                // فقط للطلب الرئيسي (الصفحة كاملة) لا الموارد الفرعية
                if (request?.isForMainFrame == true) {
                    if (!isNetworkAvailable()) {
                        // لا يوجد إنترنت — نحاول تحميل cache
                        webView.loadUrl("file:///android_asset/offline.html")
                    }
                }
            }
        }

        // ── WebChromeClient (Audio / Video / Camera) ──────────────
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                // منح الأذونات تلقائياً للميكروفون والكاميرا
                request?.grant(request.resources)
            }
        }

        // ── Load Dashboard ────────────────────────────────────────
        loadDashboard()
    }

    private fun loadDashboard() {
        // أضف timestamp فقط إذا لا يوجد cache أو إنترنت متاح
        val url = if (isNetworkAvailable()) {
            "$DASHBOARD_URL?v=${System.currentTimeMillis()}"
        } else {
            DASHBOARD_URL
        }
        webView.loadUrl(url)
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val network = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(network) ?: return false
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        } else {
            @Suppress("DEPRECATION")
            cm.activeNetworkInfo?.isConnected == true
        }
    }

    // ── بناء شاشة التحميل ────────────────────────────────────────
    private fun buildLoadingView(): View {
        val container = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER
            setBackgroundColor(Color.parseColor("#020617"))
        }

        // أيقونة الدرع
        val icon = TextView(this).apply {
            text = "🛡️"
            textSize = 48f
            gravity = android.view.Gravity.CENTER
        }

        // اسم التطبيق
        val title = TextView(this).apply {
            text = "Optimization Engine"
            textSize = 18f
            setTextColor(Color.parseColor("#f1f5f9"))
            gravity = android.view.Gravity.CENTER
            setPadding(0, 16, 0, 8)
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }

        // نص التحميل
        val subtitle = TextView(this).apply {
            text = "Management Console"
            textSize = 12f
            setTextColor(Color.parseColor("#475569"))
            gravity = android.view.Gravity.CENTER
        }

        container.addView(icon)
        container.addView(title)
        container.addView(subtitle)
        return container
    }

    // ── زر الرجوع يتنقل في WebView ───────────────────────────────
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else super.onBackPressed()
    }

    // ── إيقاف مؤقت / استئناف WebView ────────────────────────────
    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        super.onPause()
        webView.onPause()
    }

    override fun onDestroy() {
        webView.destroy()
        super.onDestroy()
    }
}
