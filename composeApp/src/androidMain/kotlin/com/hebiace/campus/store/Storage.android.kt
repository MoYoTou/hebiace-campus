package com.hebiace.campus.store

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 在 MainActivity 初始化后可用 */
object AppContextHolder {
    lateinit var context: Context
}

actual object Settings {
    private fun prefs(): SharedPreferences =
        AppContextHolder.context.getSharedPreferences("hebiace_settings", Context.MODE_PRIVATE)

    actual fun getString(key: String, default: String): String = prefs().getString(key, default) ?: default
    actual fun putString(key: String, value: String) = prefs().edit().putString(key, value).apply()
    actual fun getLong(key: String, default: Long): Long = prefs().getLong(key, default)
    actual fun putLong(key: String, value: Long) = prefs().edit().putLong(key, value).apply()
    actual fun getBoolean(key: String, default: Boolean): Boolean = prefs().getBoolean(key, default)
    actual fun putBoolean(key: String, value: Boolean) = prefs().edit().putBoolean(key, value).apply()
    actual fun remove(key: String) = prefs().edit().remove(key).apply()
}

/**
 * 敏感凭证：Android Keystore + AES-GCM 加密后存 SharedPreferences。
 * 参考开源项目 youwu / xxt-Tool / linyu 的同类实现。
 */
actual object SecureStore {
    private const val PREFS = "hebiace_secure"
    private const val KEY_ALIAS = "hebiace_campus_key"

    private fun prefs(): SharedPreferences =
        AppContextHolder.context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun getOrCreateKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + ct, Base64.NO_WRAP)
    }

    private fun decrypt(data: String): String? {
        val combined = Base64.decode(data, Base64.NO_WRAP)
        val iv = combined.copyOfRange(0, 12)
        val ct = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(128, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }

    actual fun getString(key: String): String? =
        prefs().getString(key, null)?.let { runCatching { decrypt(it) }.getOrNull() }

    actual fun putString(key: String, value: String) =
        prefs().edit().putString(key, encrypt(value)).apply()

    actual fun remove(key: String) = prefs().edit().remove(key).apply()
}
