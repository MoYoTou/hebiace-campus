package com.hebiace.campus.util

import android.util.Base64
import java.security.MessageDigest

actual fun md5Digest(input: String): ByteArray =
    MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8))

actual fun base64Encode(bytes: ByteArray): String =
    Base64.encodeToString(bytes, Base64.NO_WRAP)

actual fun base64Decode(text: String): ByteArray =
    Base64.decode(text, Base64.NO_WRAP)
