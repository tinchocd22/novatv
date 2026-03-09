package com.novatv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import java.net.URL
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private lateinit var webView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        webView = WebView(this)
        setContentView(webView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }

        // Permitir fullscreen video
        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var cb: CustomViewCallback? = null
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view; cb = callback
                setContentView(view)
            }
            override fun onHideCustomView() {
                setContentView(webView)
                cb?.onCustomViewHidden(); customView = null
            }
        }

        // Puente Java ↔ JavaScript para descargar M3U
        webView.addJavascriptInterface(object : Any() {
            @android.webkit.JavascriptInterface
            fun fetchUrl(url: String): String {
                return try {
                    val conn = URL(url).openConnection()
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 15000
                    conn.readTimeout = 20000
                    conn.getInputStream().bufferedReader().readText()
                } catch (e: Exception) {
                    "ERROR:${e.message}"
                }
            }

            @android.webkit.JavascriptInterface
            fun saveData(key: String, value: String) {
                getSharedPreferences("novatv", MODE_PRIVATE).edit().putString(key, value).apply()
            }

            @android.webkit.JavascriptInterface
            fun loadData(key: String): String {
                return getSharedPreferences("novatv", MODE_PRIVATE).getString(key, "") ?: ""
            }
        }, "Android")

        webView.webViewClient = WebViewClient()
        webView.loadUrl("file:///android_asset/index.html")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() { super.onDestroy(); webView.destroy() }
}

