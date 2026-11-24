package com.hebiace.campus.data.qzxy

import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import com.hebiace.campus.store.AppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

actual suspend fun scanShowerDevices(timeoutMs: Long): List<ShowerScanResult> =
    withContext(Dispatchers.IO) {
        val manager = AppContextHolder.context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
            ?: return@withContext emptyList()
        val adapter = manager.adapter ?: return@withContext emptyList()
        val scanner = adapter.bluetoothLeScanner ?: return@withContext emptyList()

        val found = ConcurrentHashMap<String, String>()
        val cb = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val name = result.device.name ?: return
                if (name.contains("KLCXKJ-Water", ignoreCase = true)) {
                    found[result.device.address] = name
                }
            }
        }
        runCatching { scanner.startScan(cb) }
        delay(timeoutMs)
        runCatching { scanner.stopScan(cb) }
        found.map { (mac, name) -> ShowerScanResult(name, mac) }
    }
