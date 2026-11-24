package com.hebiace.campus.util

/** MD5 十六进制（小写） */
fun md5Hex(input: String): String {
    val md = md5Digest(input)
    return md.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
}

/** MD5 取后 10 位大写 —— 趣智校园的密码传输格式 */
fun md5Last10Upper(input: String): String {
    val hex = md5Hex(input)
    return if (hex.length >= 10) hex.substring(hex.length - 10).uppercase() else hex.uppercase()
}

expect fun md5Digest(input: String): ByteArray

/** 简易 Base64 编码（UTF-8） */
expect fun base64Encode(bytes: ByteArray): String

expect fun base64Decode(text: String): ByteArray
