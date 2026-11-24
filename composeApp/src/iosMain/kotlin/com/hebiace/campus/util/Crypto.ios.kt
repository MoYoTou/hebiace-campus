package com.hebiace.campus.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.CoreCrypto.CC_MD5
import platform.CoreCrypto.CC_MD5_DIGEST_LENGTH
import platform.Foundation.NSData
import platform.Foundation.create

@OptIn(ExperimentalForeignApi::class)
actual fun md5Digest(input: String): ByteArray {
    val data = input.encodeToByteArray()
    val digest = ByteArray(CC_MD5_DIGEST_LENGTH)
    data.usePinned { inputPinned ->
        digest.usePinned { digestPinned ->
            CC_MD5(inputPinned.addressOf(0), data.size.toUInt(), digestPinned.addressOf(0))
        }
    }
    return digest
}

@OptIn(ExperimentalForeignApi::class)
actual fun base64Encode(bytes: ByteArray): String {
    val data = bytes.usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = bytes.size.toULong())
    }
    return data.base64EncodedStringWithOptions(0u)
}

@OptIn(ExperimentalForeignApi::class)
actual fun base64Decode(text: String): ByteArray {
    val data = platform.Foundation.NSData.create(base64EncodedString = text, options = 0u)
        ?: return ByteArray(0)
    val size = data.length.toInt()
    if (size <= 0) return ByteArray(0)
    val out = ByteArray(size)
    out.usePinned { pinned ->
        data.getBytes(pinned.addressOf(0), length = data.length)
    }
    return out
}
