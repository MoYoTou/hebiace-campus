package com.hebiace.campus.data.qr

/**
 * 打开相机扫描二维码，返回扫描到的文本；用户取消返回 null。
 * Android 用 zxing-android-embedded；iOS 暂未实现（返回 null）。
 */
expect suspend fun scanQrCode(prompt: String): String?
