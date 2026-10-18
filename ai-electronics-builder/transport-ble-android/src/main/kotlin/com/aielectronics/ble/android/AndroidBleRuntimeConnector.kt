package com.aielectronics.ble.android

import android.content.Context
import com.aielectronics.ble.BleRuntimeSession
import com.aielectronics.ble.BleRuntimeTransport
import com.aielectronics.ble.RuntimeCapabilities

data class AndroidBleRuntimeConnection(
    val packetIo: AndroidGattBlePacketIo,
    val transport: BleRuntimeTransport,
    val session: BleRuntimeSession,
    val capabilities: RuntimeCapabilities,
)

class AndroidBleRuntimeConnector(
    private val context: Context,
    private val profile: BleGattProfile = BleGattProfile(),
) {
    fun scanAndConnect(
        scanTimeoutMs: Long = 10_000,
    ): Result<AndroidBleRuntimeConnection> = runCatching {
        val device = AndroidBleScanner(context, profile)
            .findFirst(scanTimeoutMs)
            .getOrThrow()

        val packetIo = AndroidGattBlePacketIo(
            context = context,
            device = device,
            profile = profile,
        )
        packetIo.connect().getOrThrow()

        val transport = BleRuntimeTransport(packetIo)
        val session = BleRuntimeSession(transport)
        val capabilities =
            session.handshake().getOrThrow()

        AndroidBleRuntimeConnection(
            packetIo = packetIo,
            transport = transport,
            session = session,
            capabilities = capabilities,
        )
    }
}
