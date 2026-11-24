package com.hebiace.campus.data.web

/**
 * 打开内置 WebView 完成登录（学习通/雨课堂扫码或账密），返回 Cookie 字符串。
 * 返回空串表示用户取消。
 */
expect suspend fun openWebLogin(url: String): String
