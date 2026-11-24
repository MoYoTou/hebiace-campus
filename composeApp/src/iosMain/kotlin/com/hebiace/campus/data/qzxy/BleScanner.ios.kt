package com.hebiace.campus.data.qzxy

/** iOS 暂未实现 BLE 扫描（后续用 CoreBluetooth 实现，过滤名 KLCXKJ-Water） */
actual suspend fun scanShowerDevices(timeoutMs: Long): List<ShowerScanResult> = emptyList()
