package com.hebiace.campus.data.web

/**
 * 内置 WebView 登录配置。
 * @param successHosts 登录成功后跳转到的域名（逗号分隔，空=不限域名，仅凭 Cookie 判断）
 * @param successCookieName 登录成功才会出现的会话 Cookie 名（逗号分隔，命中其一即认为成功）
 */
data class WebLoginSpec(
    val url: String,
    val successHosts: String = "",
    val successCookieName: String = "",
)

/**
 * 打开内置 WebView 完成登录（学习通/雨课堂扫码或账密）。
 * 登录成功后自动返回 Cookie 字符串；返回空串表示用户取消。
 */
expect suspend fun openWebLogin(spec: WebLoginSpec): String
