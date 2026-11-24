package com.hebiace.campus.data

import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.HttpCookies

/** 各模块共用的 HttpClient：带 Cookie 罐 + 超时。引擎由各平台依赖注入（Android=OkHttp / iOS=Darwin）。 */
fun createHttpClient(): HttpClient = HttpClient {
    install(HttpCookies)
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        requestTimeoutMillis = 30_000
        socketTimeoutMillis = 30_000
    }
    expectSuccess = false
}
