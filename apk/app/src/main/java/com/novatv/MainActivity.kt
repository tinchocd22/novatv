package com.novatv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import android.widget.FrameLayout
import android.widget.ProgressBar
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*

class MainActivity : Activity() {

    private lateinit var webView: WebView
    private lateinit var progress: ProgressBar
    private var serverStarted = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Layout
        val frame = FrameLayout(this)
        progress = ProgressBar(this).apply {
            isIndeterminate = true
        }
        webView = WebView(this).apply {
            visibility = View.GONE
        }
        frame.addView(webView, FrameLayout.LayoutParams(-1, -1))
        frame.addView(progress, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.CENTER
        ))
        setContentView(frame)

        setupWebView()
        startFlaskServer()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            // TV: enable hardware acceleration
            setRenderPriority(WebSettings.RenderPriority.HIGH)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                webView.visibility = View.VISIBLE
            }
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                // Retry after 1s if server not ready yet
                if (!serverStarted) {
                    webView.postDelayed({ webView.reload() }, 1000)
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            // Allow fullscreen video
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view
                customViewCallback = callback
                (webView.parent as FrameLayout).addView(view, FrameLayout.LayoutParams(-1, -1))
                webView.visibility = View.GONE
            }

            override fun onHideCustomView() {
                (webView.parent as FrameLayout).removeView(customView)
                webView.visibility = View.VISIBLE
                customViewCallback?.onCustomViewHidden()
                customView = null
            }
        }
    }

    private fun startFlaskServer() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Init Python
                if (!Python.isStarted()) {
                    Python.start(AndroidPlatform(this@MainActivity))
                }
                val py = Python.getInstance()

                // Copy static files to app's files dir
                copyAssets()

                // Start Flask in background thread
                val serverModule = py.getModule("server")
                serverModule.callAttr("start_server", filesDir.absolutePath)

                serverStarted = true

                // Load UI
                withContext(Dispatchers.Main) {
                    webView.loadUrl("http://localhost:5000")
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    // Show error in WebView
                    webView.visibility = View.VISIBLE
                    progress.visibility = View.GONE
                    webView.loadData(
                        "<html><body style='background:#000;color:#fff;font-family:sans-serif;padding:40px'>" +
                        "<h2>Error al iniciar servidor</h2><p>${e.message}</p></body></html>",
                        "text/html", "utf-8"
                    )
                }
            }
        }

        // Try loading after 3 seconds regardless
        webView.postDelayed({
            if (webView.visibility == View.GONE) {
                webView.loadUrl("http://localhost:5000")
            }
        }, 3000)
    }

    private fun copyAssets() {
        val staticDir = java.io.File(filesDir, "static")
        staticDir.mkdirs()

        // Copy index.html
        try {
            assets.open("index.html").use { inp ->
                java.io.File(staticDir, "index.html").outputStream().use { out ->
                    inp.copyTo(out)
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Back button
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            if (webView.canGoBack()) {
                webView.goBack()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        webView.destroy()
    }
}
