package com.hebiace.campus.store

/** 非敏感设置存储（跨平台）。Android=SharedPreferences，iOS=NSUserDefaults */
expect object Settings {
    fun getString(key: String, default: String = ""): String
    fun putString(key: String, value: String)
    fun getLong(key: String, default: Long = 0L): Long
    fun putLong(key: String, value: Long)
    fun getBoolean(key: String, default: Boolean = false): Boolean
    fun putBoolean(key: String, value: Boolean)
    fun remove(key: String)
}

/** 敏感凭证存储（跨平台）。Android=Keystore+AES-GCM，iOS=Keychain */
expect object SecureStore {
    fun getString(key: String): String?
    fun putString(key: String, value: String)
    fun remove(key: String)
}
