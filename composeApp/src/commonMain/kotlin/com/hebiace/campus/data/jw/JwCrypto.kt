package com.hebiace.campus.data.jw

/**
 * 新正方（jwglxt）登录密码加密的跨平台抽象。
 * 算法（源自各开源正方课表项目、与正方登录页 JS 一致）：
 *  1. aesKey = 16 位随机字母数字
 *  2. mm 密码 = RSA(aesKey) + AES(password)
 *  3. RSA：m = aesKey 的 UTF-8 字节按大端转整数，c = m^e mod n，
 *     结果补足 128 字节后反转字节序，再 Base64
 *  4. AES：AES-128-CBC，key=iv=aesKey 的 UTF-8 字节，PKCS5 填充，Base64
 */
expect fun jwRsaEncrypt(aesKey: String, modulus: String, exponent: String): String

expect fun jwAesEncrypt(plain: String, aesKey: String): String

fun randomAesKey(): String {
    val chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
    return buildString {
        repeat(16) { append(chars.random()) }
    }
}

/** 组装 mm：RSA(key) 串接 AES(password) */
fun jwEncryptPassword(password: String, modulus: String, exponent: String): String {
    val key = randomAesKey()
    return jwRsaEncrypt(key, modulus, exponent) + jwAesEncrypt(password, key)
}
