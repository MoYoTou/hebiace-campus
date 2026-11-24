package com.hebiace.campus.ui

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity

/** 学习页 WebView：注入已保存的登录 Cookie，全屏浏览课程/作业内容。 */
class LearnWebActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = intent.getStringExtra("url") ?: "https://mooc1.chaoxing.com"
        val cookie = intent.getStringExtra("cookie") ?: ""
        val title = intent.getStringExtra("title") ?: "学习"
        val cookieHosts = intent.getStringExtra("cookieHosts") ?: ""

        injectCookies(cookie, cookieHosts, url)

        val webView = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.javaScriptCanOpenWindowsAutomatically = true
            settings.userAgentString =
                "Mozilla/5.0 (Linux; Android 14; Pixel 6) AppleWebKit/537.36 " +
                    "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return false // 站内跳转留在本 WebView
                }
            }
        }

        val back = Button(this).apply {
            text = "‹ 返回"
            setTextColor(Color.parseColor("#185FA5"))
            setBackgroundColor(Color.TRANSPARENT)
            gravity = Gravity.CENTER_VERTICAL
            setOnClickListener { finish() }
        }
        val titleView = TextView(this).apply {
            text = title
            setTextColor(Color.parseColor("#1F1F1F"))
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER_VERTICAL
            maxLines = 1
        }

        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(8, 0, 8, 0)
            addView(back, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (48 * resources.displayMetrics.density).toInt()))
            addView(titleView, LinearLayout.LayoutParams(0, (48 * resources.displayMetrics.density).toInt(), 1f))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(top)
            addView(webView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        setContentView(root)
        webView.loadUrl(url)
    }

    private fun injectCookies(cookie: String, cookieHosts: String, url: String) {
        if (cookie.isBlank()) return
        val hosts = linkedSetOf<String>()
        cookieHosts.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { hosts.add(it) }
        runCatching { java.net.URI(url).host }.getOrNull()?.let { hosts.add(it) }

        val cm = CookieManager.getInstance()
        for (h in hosts) {
            runCatching { cm.setCookie("https://$h", cookie) }
        }
        cm.flush()
    }
}
