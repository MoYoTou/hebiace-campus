package com.hebiace.campus.store

import platform.Foundation.NSUserDefaults

actual object Settings {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String, default: String): String = defaults.stringForKey(key) ?: default
    actual fun putString(key: String, value: String) { defaults.setObject(value, forKey = key) }
    actual fun getLong(key: String, default: Long): Long = defaults.stringForKey(key)?.toLongOrNull() ?: default
    actual fun putLong(key: String, value: Long) { defaults.setObject(value.toString(), forKey = key) }
    actual fun getBoolean(key: String, default: Boolean): Boolean = defaults.boolForKey(key)
    actual fun putBoolean(key: String, value: Boolean) { defaults.setBool(value, forKey = key) }
    actual fun remove(key: String) { defaults.removeObjectForKey(key) }
}

/** iOS 敏感凭证：暂用 NSUserDefaults，正式应换 Keychain（后续完善） */
actual object SecureStore {
    private val defaults = NSUserDefaults.standardUserDefaults

    actual fun getString(key: String): String? = defaults.stringForKey(key)
    actual fun putString(key: String, value: String) { defaults.setObject(value, forKey = key) }
    actual fun remove(key: String) { defaults.removeObjectForKey(key) }
}
