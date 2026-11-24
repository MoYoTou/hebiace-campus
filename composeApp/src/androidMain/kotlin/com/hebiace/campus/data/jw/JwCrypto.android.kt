package com.hebiace.campus.data.jw

import com.hebiace.campus.util.base64Encode
import java.math.BigInteger
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * 新正方登录加密（Android 实现）。
 * RSA 用 BigInteger.modPow（无填充）；AES 用 javax.crypto。
 */
actual fun jwRsaEncrypt(aesKey: String, modulus: String, exponent: String): String {
    val m = BigInteger(1, aesKey.toByteArray(Charsets.UTF_8))
    val n = BigInteger(modulus, 16)
    val e = BigInteger(exponent, 16)
    val c = m.modPow(e, n)
    var raw = c.toByteArray()
    // BigInteger.toByteArray 可能带符号前导 0x00
    if (raw.size > 128 && raw[0] == 0.toByte()) raw = raw.copyOfRange(1, raw.size)
    val padded = ByteArray(128)
    raw.copyInto(padded, 128 - raw.size)
    padded.reverse()
    return base64Encode(padded)
}

actual fun jwAesEncrypt(plain: String, aesKey: String): String {
    val key = aesKey.toByteArray(Charsets.UTF_8)
    val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
    cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), IvParameterSpec(key))
    return base64Encode(cipher.doFinal(plain.toByteArray(Charsets.UTF_8)))
}
