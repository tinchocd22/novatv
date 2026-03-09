package com.novatv

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.webkit.*
import fi.iki.elonen.NanoHTTPD
import java.io.InputStream

// Mini servidor HTTP local en puerto 8080
class AssetServer(private val activity: Activity) : NanoHTTPD("127.0.0.1", 8080) {
    override fun serve(session: IHTTPSession): Response {
        return try {
            val path = if (session.uri == "/" || session.uri == "") "index.html"
                       else session.uri.trimStart('/')
            val stream: InputStream = activity.assets.open(path)
            val mime = when {
                path.endsWith(".html") -> "text/html"
                path.endsWith(".js")   -> "application/javascript"
                path.endsWith(".css")  -> "text/css"
                else -> "application/octet-stream"
            }
            newChunkedResponse(Response.Status.OK, mime, stream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, "text/plain", "Not found")
        }
    }
}

class MainActivity : Activity() {
    private lateinit var webView: WebView
    private var server: AssetServer? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        // Iniciar servidor local
        server = AssetServer(this)
        server?.start()

        webView = WebView(this)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            cacheMode = WebSettings.LOAD_DEFAULT
        }
        webView.webChromeClient = object : WebChromeClient() {
            private var customView: View? = null
            private var customViewCallback: CustomViewCallback? = null
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                customView = view; customViewCallback = callback
                setContentView(view)
            }
            override fun onHideCustomView() {
                setContentView(webView)
                customViewCallback?.onCustomViewHidden(); customView = null
            }
        }
        webView.webViewClient = WebViewClient()
        setContentView(webView)

        // Cargar desde servidor local en vez de file://
        webView.loadUrl("http://127.0.0.1:8080/")
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack(); return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        webView.destroy()
    }
}
