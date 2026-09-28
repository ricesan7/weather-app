package com.aielectronics.ble

interface BlePacketIo {
    val isConnected: Boolean

    fun connect(): Result<Unit>
    fun disconnect()

    /**
     * Maximum application packet bytes available to the packetizer.
     * Android GATT adapters should return negotiated ATT_MTU - 3.
     */
    fun maxPacketBytes(): Int

    /**
     * Sends one logical request as ordered BLE packets and returns the ordered
     * response notification packets for the same logical transaction.
     */
    fun transact(requestPackets: List<ByteArray>): Result<List<ByteArray>>
}

class BleDisconnectedException(
    message: String = "BLE link disconnected",
) : RuntimeException(message)

class BleProtocolMismatchException(
    message: String,
) : RuntimeException(message)

class BleRequestMismatchException(
    message: String,
) : RuntimeException(message)
