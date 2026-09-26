package com.aielectronics.ble.android

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class AndroidBleScanner(
    context: Context,
    private val profile: BleGattProfile = BleGattProfile(),
) {
    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager =
        appContext.getSystemService(BluetoothManager::class.java)

    @SuppressLint("MissingPermission")
    fun findFirst(timeoutMs: Long = 10_000): Result<BluetoothDevice> = runCatching {
        val adapter = bluetoothManager.adapter
            ?: error("Bluetooth is not supported")
        check(adapter.isEnabled) { "Bluetooth is disabled" }

        val scanner = adapter.bluetoothLeScanner
            ?: error("BLE scanner is unavailable")

        val found = AtomicReference<BluetoothDevice?>()
        val failure = AtomicReference<Int?>()
        val latch = CountDownLatch(1)

        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (found.compareAndSet(null, result.device)) {
                    latch.countDown()
                }
            }

            override fun onScanFailed(errorCode: Int) {
                failure.set(errorCode)
                latch.countDown()
            }
        }

        val filter = ScanFilter.Builder()
            .setServiceUuid(ParcelUuid(profile.serviceUuid))
            .build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            scanner.startScan(listOf(filter), settings, callback)
            check(latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                "BLE scan timed out"
            }
        } finally {
            scanner.stopScan(callback)
        }

        failure.get()?.let { code ->
            error("BLE scan failed: $code")
        }

        found.get() ?: error("No compatible device found")
    }
}
