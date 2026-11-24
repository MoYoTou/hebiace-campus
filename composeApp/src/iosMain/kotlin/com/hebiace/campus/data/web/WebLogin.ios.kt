package com.hebiace.campus.data.web

/** iOS 暂未实现内置 WebView 登录（后续用 WKWebView 实现） */
actual suspend fun openWebLogin(spec: WebLoginSpec): String = ""
