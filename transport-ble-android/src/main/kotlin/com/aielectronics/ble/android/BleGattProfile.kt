package com.aielectronics.ble.android

import java.util.UUID

data class BleGattProfile(
    val serviceUuid: UUID = SERVICE_UUID,
    val writeCharacteristicUuid: UUID = WRITE_UUID,
    val notifyCharacteristicUuid: UUID = NOTIFY_UUID,
    val requestedMtu: Int = 247,
    val connectTimeoutMs: Long = 12_000,
    val operationTimeoutMs: Long = 8_000,
    val responseTimeoutMs: Long = 12_000,
) {
    companion object {
        val SERVICE_UUID: UUID =
            UUID.fromString("f4a10000-7f6a-4c7a-9e4f-3d6a2c4b1000")
        val WRITE_UUID: UUID =
            UUID.fromString("f4a10001-7f6a-4c7a-9e4f-3d6a2c4b1000")
        val NOTIFY_UUID: UUID =
            UUID.fromString("f4a10002-7f6a-4c7a-9e4f-3d6a2c4b1000")
        val CCCD_UUID: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
