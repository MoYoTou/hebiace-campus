package com.hebiace.campus.data.jw

/**
 * 新正方登录加密（iOS 待实现）。
 * 需用 CommonCrypto 做 AES-CBC，并用大整数做 RSA modPow；后续在 Mac 上完善。
 */
actual fun jwRsaEncrypt(aesKey: String, modulus: String, exponent: String): String =
    throw NotImplementedError("iOS 教务登录加密待实现（RSA）")

actual fun jwAesEncrypt(plain: String, aesKey: String): String =
    throw NotImplementedError("iOS 教务登录加密待实现（AES）")
