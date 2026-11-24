package com.hebiace.campus.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import androidx.activity.ComponentActivity
import com.hebiace.campus.data.web.finishWebLogin

/** 内置 WebView 登录页（学习通 / 雨课堂）。登录成功后自动回传 Cookie 并关闭，也可点「完成登录」手动返回。 */
class LoginWebActivity : ComponentActivity() {

    private var lastUrl = ""
    private var loginUrl = ""
    private var successHosts = ""
    private var successCookieName = ""
    private var finished = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "https://i.chaoxing.com"
        lastUrl = url
        loginUrl = url
        successHosts = intent.getStringExtra("successHosts") ?: ""
        successCookieName = intent.getStringExtra("successCookieName") ?: ""

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let {
                        lastUrl = it
                        maybeAutoFinish(it)
                    }
                    return false
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let {
                        lastUrl = it
                        maybeAutoFinish(it)
                    }
                }
            }
        }

        val done = Button(this).apply {
            text = "完成登录"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#185FA5"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                (48 * resources.displayMetrics.density).toInt(),
            )
            setOnClickListener { finishWithCookie() }
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            addView(done)
            addView(
                webView,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f),
            )
        }
        setContentView(root)
        webView.loadUrl(url)
    }

    /** 判断是否已登录成功：URL 命中成功域名 且 会话 Cookie 已出现 */
    private fun maybeAutoFinish(url: String) {
        if (finished) return
        if (!hostMatches(url)) return
        val cookie = collectCookie()
        if (!cookieMatches(cookie)) return
        finished = true
        finishWebLogin(cookie)
        finish()
    }

    private fun hostMatches(url: String): Boolean {
        if (successHosts.isBlank()) return true
        val host = runCatching { java.net.URI(url).host }.getOrNull() ?: return false
        return successHosts.split(',').any { h ->
            val t = h.trim()
            t.isNotEmpty() && (host == t || host.endsWith(".$t"))
        }
    }

    private fun cookieMatches(cookie: String): Boolean {
        val names = successCookieName.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (names.isEmpty()) return true
        return names.any { n -> cookie.split(';').any { it.trim().startsWith("$n=") } }
    }

    private fun collectCookie(): String {
        // 从登录页域名 + 当前跳转后域名分别取 Cookie，按名字去重合并，避免遗漏会话
        val hosts = linkedSetOf<String>()
        runCatching { java.net.URI(loginUrl).host }.getOrNull()?.let { hosts.add(it) }
        runCatching { java.net.URI(lastUrl).host }.getOrNull()?.let { hosts.add(it) }

        val cookies = linkedMapOf<String, String>()
        for (h in hosts) {
            if (h.isNullOrBlank()) continue
            val raw = runCatching {
                CookieManager.getInstance().getCookie("https://$h")
            }.getOrNull() ?: ""
            for (part in raw.split(';')) {
                val idx = part.indexOf('=')
                if (idx > 0) cookies[part.substring(0, idx).trim()] = part.substring(idx + 1).trim()
            }
        }
        return cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    private fun finishWithCookie() {
        if (finished) return
        val cookie = collectCookie()
        // 仅当检测到会话 Cookie 时才视为登录成功，否则视为取消（返回空串）
        val result = if (cookieMatches(cookie)) cookie else ""
        finished = true
        finishWebLogin(result)
        finish()
    }

    override fun onBackPressed() {
        finishWithCookie()
    }
}
