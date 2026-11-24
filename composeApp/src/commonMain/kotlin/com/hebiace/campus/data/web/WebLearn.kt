package com.hebiace.campus.data.web

/**
 * 打开内置 WebView 学习页（已登录态），注入登录 Cookie，全屏浏览课程/作业内容。
 * @param cookie 该平台已保存的 Cookie 串
 * @param cookieHosts 需要注入 Cookie 的域名（逗号分隔）
 */
expect fun openWebLearn(url: String, cookie: String, title: String, cookieHosts: String)
