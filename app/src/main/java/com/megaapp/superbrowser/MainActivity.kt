package com.megaapp.superbrowser

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.ui.platform.ComposeView
import androidx.core.view.WindowCompat
import androidx.core.view.setOnApplyWindowInsetsListener
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

/**
 * MainActivity - Entry point with WebView and JS Bridge
 * Phase 0: Basic WebView + MinisBridge + DevTools
 */
class MainActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var minisBridge: MinisBridge? = null
    private val tag = "MainActivity"

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface", "AddJavascriptInterface")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(tag, "onCreate")

        // Edge-to-edge display
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setStatusBarColor(android.graphics.Color.TRANSPARENT)
        window.setNavigationBarColor(android.graphics.Color.TRANSPARENT)
        window.decorView.setOnApplyWindowInsetsListener { view, insets ->
            val imeInsets = insets.getInsets(WindowInsetsCompat.Type.ime())
            val sysInsets = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(0, sysInsets.top, 0, sysInsets.bottom)
            insets
        }

        // Create WebView programmatically (no XML layout needed)
        setupWebView()

        // Load initial page
        loadInitialPage()
    }

    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun setupWebView() {
        val wv = WebView(this).apply {
            id = View.generateViewId()
            layoutParams = android.view.ViewGroup.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val settings = wv.settings.apply {
            // JavaScript
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            allowFileAccessFromFileURLs = true
            allowUniversalAccessFromFileURLs = true

            // Rendering
            loadWithOverviewMode = true
            useWideViewPort = true
            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            javaScriptCanOpenWindowsAutomatically = true
            supportMultipleWindows = true

            // Caching
            cacheMode = WebSettings.LOAD_DEFAULT
            setAppCacheEnabled(true)
            setAppCachePath(cacheDir.absolutePath)

            // User agent
            userAgentString = "$userAgentString MegaApp/0.1.0"

            // Media
            mediaPlaybackRequiresUserGesture = false
        }

        // Enable DevTools debugging (CRITICAL for Kiwi Browser / chrome://inspect)
        if (BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
            Log.d(tag, "WebView DevTools ENABLED - inspect at chrome://inspect")
        }

        // WebViewClient for navigation handling
        wv.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val url = request.url.toString()
                Log.d(tag, "Navigation: $url")
                if (url.startsWith("megaapp://")) {
                    handleDeepLink(url)
                    return true
                }
                return false
            }

            override fun onPageFinished(view: WebView, url: String) {
                Log.d(tag, "Page loaded: $url")
                injectBridgeScript()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                Log.w(tag, "SSL Error: ${error.primaryError} - ${error.url}")
                handler.proceed() // Allow for localhost/dev
            }
        }

        // WebChromeClient for console, permissions, etc.
        wv.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage): Boolean {
                Log.d("WebView-Console", "[${consoleMessage.messageLevel()}] ${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})")
                return true
            }

            override fun onPermissionRequest(request: android.webkit.PermissionRequest) {
                request.grant(request.resources)
            }

            override fun onProgressChanged(view: WebView, newProgress: Int) {
                // Could show progress bar
            }
        }

        // Add JavaScript Interface
        minisBridge = MinisBridge(wv, this)
        wv.addJavascriptInterface(minisBridge, "minis")

        // Cookie manager
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

        webView = wv
        setContentView(wv)
    }

    private fun loadInitialPage() {
        webView?.loadUrl("file:///android_asset/index.html")
    }

    private fun injectBridgeScript() {
        val script = """
            (function() {
                if (window.minisBridgeReady) return;
                window.minisBridgeReady = true;

                // Helper: Call bridge with callback
                window.minis.call = function(method, ...args) {
                    return new Promise((resolve, reject) => {
                        const callbackName = 'cb_' + Date.now() + '_' + Math.random().toString(36).substr(2, 9);
                        window.minis[callbackName] = function(result) {
                            delete window.minis[callbackName];
                            if (result.success) resolve(result);
                            else reject(result.error || 'Unknown error');
                        };
                        try {
                            window.minis[method].apply(window.minis, [...args, callbackName]);
                        } catch (e) {
                            reject(e.message);
                        }
                    });
                };

                // Convenience methods
                window.minis.ping = () => window.minis.call('ping');
                window.minis.getAppInfo = () => window.minis.call('getAppInfo');
                window.minis.toast = (msg) => window.minis.call('showToast', msg);
                window.minis.log = (level, msg) => window.minis.call('log', level, msg);

                // Termux (Phase 1+)
                window.minis.termux = {
                    exec: (cmd) => window.minis.call('termuxExec', cmd),
                    test: () => window.minis.call('termuxTestConnection')
                };

                // MCP (Phase 2+)
                window.minis.mcp = {
                    callTool: (server, tool, args) => window.minis.call('mcpCallTool', server, tool, JSON.stringify(args)),
                    listTools: (server) => window.minis.call('mcpListTools', server),
                    listServers: () => window.minis.call('mcpListServers')
                };

                // AI (Phase 4+)
                window.minis.ai = {
                    chat: (model, messages) => window.minis.call('aiChat', model, JSON.stringify(messages)),
                    listModels: () => window.minis.call('aiListModels')
                };

                // Storage
                window.minis.storage = {
                    get: (key) => window.minis.call('storageGet', key),
                    set: (key, value) => window.minis.call('storageSet', key, value)
                };

                // Notify ready
                document.dispatchEvent(new CustomEvent('minisReady', { detail: { version: '0.1.0' } }));
            })();
        """.trimIndent()

        webView?.evaluateJavascript(script, null)
    }

    private fun handleDeepLink(url: String) {
        Log.d(tag, "Deep link: $url")
        // TODO: Handle megaapp:// deep links
    }

    override fun onBackPressed() {
        webView?.let { wv ->
            if (wv.canGoBack()) {
                wv.goBack()
            } else {
                super.onBackPressed()
            }
        } ?: super.onBackPressed()
    }

    override fun onDestroy() {
        webView?.destroy()
        webView = null
        minisBridge = null
        super.onDestroy()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        // Forward to WebView if needed
    }

    companion object {
        const val EXTRA_URL = "extra_url"
    }
}