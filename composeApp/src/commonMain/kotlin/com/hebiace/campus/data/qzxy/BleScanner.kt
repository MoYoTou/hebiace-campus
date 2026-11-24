package com.hebiace.campus.data.qzxy

data class ShowerScanResult(val name: String, val mac: String)

/**
 * 扫描附近趣智校园热水器（BLE，广播名含 KLCXKJ-Water）。
 * Android 用 BluetoothLeScanner；iOS 暂未实现（返回空）。
 */
expect suspend fun scanShowerDevices(timeoutMs: Long = 8000): List<ShowerScanResult>
