package com.aielectronics.ble

import com.aielectronics.runtime.RuntimeFrame
import com.aielectronics.runtime.RuntimeFrameCodec
import com.aielectronics.runtime.RuntimeTransport
import java.util.concurrent.atomic.AtomicInteger

class BleRuntimeTransport(
    private val io: BlePacketIo,
    private val supportedProtocolVersion: Int = 1,
    private val reconnectAttempts: Int = 1,
    private val packetizer: BlePacketizer = BlePacketizer(),
) : RuntimeTransport {

    private val nextMessageId = AtomicInteger(1)

    override fun exchange(frame: RuntimeFrame): Result<RuntimeFrame> = runCatching {
        require(frame.protocolVersion == supportedProtocolVersion) {
            "Outgoing runtime protocol version is unsupported"
        }

        ensureConnected()

        val payload = RuntimeFrameCodec
            .encode(frame)
            .toByteArray(Charsets.UTF_8)

        val messageId = nextMessageId.getAndUpdate { current ->
            if (current >= 0xFFFF) 1 else current + 1
        }

        val responsePackets = transactWithReconnect(
            messageId = messageId,
            payload = payload,
        )

        val responseText = packetizer
            .join(responsePackets)
            .toString(Charsets.UTF_8)

        val response = RuntimeFrameCodec.decode(responseText)

        if (response.protocolVersion != supportedProtocolVersion) {
            throw BleProtocolMismatchException(
                "Runtime protocol version " + response.protocolVersion +
                    " is not supported; expected " + supportedProtocolVersion
            )
        }

        if (response.requestId != frame.requestId) {
            throw BleRequestMismatchException(
                "Runtime response requestId mismatch"
            )
        }

        response
    }

    private fun ensureConnected() {
        if (io.isConnected) return
        io.connect().getOrThrow()
    }

    private fun transactWithReconnect(
        messageId: Int,
        payload: ByteArray,
    ): List<ByteArray> {
        var attempt = 0
        var lastFailure: Throwable? = null

        while (attempt <= reconnectAttempts) {
            ensureConnected()
            val requestPackets = packetizer.split(
                messageId = messageId,
                payload = payload,
                maxPacketBytes = io.maxPacketBytes(),
            )

            val result = io.transact(requestPackets)
            if (result.isSuccess) {
                return result.getOrThrow()
            }

            val failure = result.exceptionOrNull()
                ?: IllegalStateException("BLE transaction failed")
            lastFailure = failure

            val reconnectable =
                failure is BleDisconnectedException || !io.isConnected

            if (!reconnectable || attempt >= reconnectAttempts) {
                throw failure
            }

            io.disconnect()
            io.connect().getOrThrow()
            attempt += 1
        }

        throw lastFailure ?: IllegalStateException("BLE transaction failed")
    }
}
