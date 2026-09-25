package com.megaapp.superbrowser

import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.squareup.moshi.Json
import com.squareup.moshi.Moshi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * MinisBridge - JavaScript ↔ Kotlin bridge for WebView
 * Exposes native functionality to WebView JavaScript context
 */
class MinisBridge(
    private val webView: WebView,
    private val mainActivity: MainActivity
) {

    private val moshi = Moshi.Builder().build()
    private val scope = CoroutineScope(Dispatchers.IO)
    private val uiHandler = Handler(Looper.getMainLooper())

    // ===== BASE METHODS =====

    @JavascriptInterface
    fun ping(): String {
        Log.d("MinisBridge", "ping() called from JS")
        return "pong"
    }

    @JavascriptInterface
    fun getAppInfo(): String {
        return moshi.adapter(AppInfo::class.java).toJson(AppInfo(
            name = "MegaApp SuperBrowser",
            version = "0.1.0",
            packageName = mainActivity.packageName,
            sdkInt = android.os.Build.VERSION.SDK_INT,
            webViewVersion = android.webkit.WebView.getWebViewPackageName(mainActivity) ?: "unknown"
        ))
    }

    @JavascriptInterface
    fun showToast(message: String) {
        uiHandler.post {
            android.widget.Toast.makeText(mainActivity, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun log(level: String, message: String) {
        when (level.lowercase()) {
            "d", "debug" -> Log.d("MegaApp-JS", message)
            "i", "info" -> Log.i("MegaApp-JS", message)
            "w", "warn" -> Log.w("MegaApp-JS", message)
            "e", "error" -> Log.e("MegaApp-JS", message)
            else -> Log.v("MegaApp-JS", message)
        }
    }

    // ===== TERMUX METHODS (Phase 1+) =====

    @JavascriptInterface
    fun termuxExec(command: String, callback: String) {
        Log.d("MinisBridge", "termuxExec requested: $command")
        scope.launch {
            // TODO Phase 1: Implement SSH to Termux
            val result = TermuxResult(
                success = false,
                stdout = "",
                stderr = "Termux integration not implemented yet (Phase 1)",
                exitCode = -1
            )
            callbackToJs(callback, result)
        }
    }

    @JavascriptInterface
    fun termuxTestConnection(callback: String) {
        Log.d("MinisBridge", "termuxTestConnection requested")
        scope.launch {
            val result = TermuxResult(
                success = false,
                stdout = "",
                stderr = "Phase 1 pending",
                exitCode = -1
            )
            callbackToJs(callback, result)
        }
    }

    // ===== MCP METHODS (Phase 2+) =====

    @JavascriptInterface
    fun mcpCallTool(server: String, tool: String, argsJson: String, callback: String) {
        Log.d("MinisBridge", "mcpCallTool: $server.$tool")
        scope.launch {
            val result = McpResult(
                success = false,
                data = "",
                error = "MCP integration not implemented yet (Phase 2)"
            )
            callbackToJs(callback, result)
        }
    }

    @JavascriptInterface
    fun mcpListTools(server: String, callback: String) {
        scope.launch {
            val result = McpListResult(
                success = false,
                tools = emptyList(),
                error = "Phase 2 pending"
            )
            callbackToJs(callback, result)
        }
    }

    @JavascriptInterface
    fun mcpListServers(callback: String) {
        scope.launch {
            val servers = listOf(
                "model-router", "budget-optimizer", "eval-harness", "local-model-manager",
                "graph-db", "vector_db", "digital-storage", "web-researcher",
                "free-model-system", "visual-memory", "multi-personality",
                "unified-orchestrator", "graphrag-engine", "hybrid-memory"
            )
            val result = McpServersResult(
                success = true,
                servers = servers,
                error = ""
            )
            callbackToJs(callback, result)
        }
    }

    // ===== AI PROVIDER METHODS (Phase 4+) =====

    @JavascriptInterface
    fun aiChat(model: String, messagesJson: String, callback: String) {
        Log.d("MinisBridge", "aiChat requested: $model")
        scope.launch {
            val result = AiResult(
                success = false,
                content = "",
                error = "AI provider integration not implemented yet (Phase 4)"
            )
            callbackToJs(callback, result)
        }
    }

    @JavascriptInterface
    fun aiListModels(callback: String) {
        scope.launch {
            val models = listOf(
                "gemini-3.5-flash-lite", "gemini-3.6-flash", "gemini-3.7-flash",
                "gpt-4o", "gpt-4o-mini", "claude-3.5-sonnet", "deepseek-v3",
                "llama-3.3-70b-groq", "qwen-2.5-72b"
            )
            val result = AiModelsResult(
                success = true,
                models = models,
                error = ""
            )
            callbackToJs(callback, result)
        }
    }

    // ===== STORAGE METHODS =====

    @JavascriptInterface
    fun storageGet(key: String, callback: String) {
        scope.launch {
            // TODO: Implement DataStore read
            val result = StorageResult(
                success = false,
                value = "",
                error = "Storage not implemented yet"
            )
            callbackToJs(callback, result)
        }
    }

    @JavascriptInterface
    fun storageSet(key: String, value: String, callback: String) {
        scope.launch {
            // TODO: Implement DataStore write
            val result = StorageResult(
                success = false,
                value = "",
                error = "Storage not implemented yet"
            )
            callbackToJs(callback, result)
        }
    }

    // ===== HELPER METHODS =====

    private fun <T> callbackToJs(callbackName: String, result: T) {
        val json = moshi.adapter(result::class.java).toJson(result)
        uiHandler.post {
            webView.evaluateJavascript("window.minis.$callbackName($json)", null)
        }
    }

    // ===== DATA CLASSES =====

    data class AppInfo(
        @Json(name = "name") val name: String,
        @Json(name = "version") val version: String,
        @Json(name = "packageName") val packageName: String,
        @Json(name = "sdkInt") val sdkInt: Int,
        @Json(name = "webViewVersion") val webViewVersion: String
    )

    data class TermuxResult(
        @Json(name = "success") val success: Boolean,
        @Json(name = "stdout") val stdout: String,
        @Json(name = "stderr") val stderr: String,
        @Json(name = "exitCode") val exitCode: Int
    )

    data class McpResult(
        @Json(name = "success") val success: Boolean,
        @Json(name = "data") val data: String,
        @Json(name = "error") val error: String
    )

    data class McpListResult(
        @Json(name = "success") val success: Boolean,
        @Json(name = "tools") val tools: List<String>,
        @Json(name = "error") val error: String
    )

    data class McpServersResult(
        @Json(name = "success") val success: Boolean,
        @Json(name = "servers") val servers: List<String>,
        @Json(name = "error") val error: String
    )

    data class AiResult(
        @Json(name = "success") val success: Boolean,
        @Json(name = "content") val content: String,
        @Json(name = "error") val error: String
    )

    data class AiModelsResult(
        @Json(name = "success") val success: Boolean,
        @Json(name = "models") val models: List<String>,
        @Json(name = "error") val error: String
    )

    data class StorageResult(
        @Json(name = "success") val success: Boolean,
        @Json(name = "value") val value: String,
        @Json(name = "error") val error: String
    )
}