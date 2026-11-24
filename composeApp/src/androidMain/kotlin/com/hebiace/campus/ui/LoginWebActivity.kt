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

/** 内置 WebView 登录页（学习通 / 雨课堂），登录完成后点「完成」回传 Cookie */
class LoginWebActivity : ComponentActivity() {

    private var lastUrl = ""
    private var loginUrl = ""

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "https://i.chaoxing.com"
        lastUrl = url
        loginUrl = url

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    url?.let { lastUrl = it }
                    return false
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    url?.let { lastUrl = it }
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

    private fun finishWithCookie() {
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
        val cookie = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }
        finishWebLogin(cookie)
        finish()
    }

    override fun onBackPressed() {
        finishWithCookie()
    }
}
