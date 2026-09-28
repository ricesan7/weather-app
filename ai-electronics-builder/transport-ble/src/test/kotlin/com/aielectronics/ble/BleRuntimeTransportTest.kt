package com.aielectronics.ble

import com.aielectronics.runtime.RuntimeFrame
import com.aielectronics.runtime.RuntimeFrameCodec
import com.aielectronics.runtime.RuntimeMessageType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class BleRuntimeTransportTest {

    @Test
    fun `packetizer round trips payload across small BLE packets`() {
        val packetizer = BlePacketizer()
        val payload = ("manifest-" + "x".repeat(400)).toByteArray()

        val packets = packetizer.split(
            messageId = 42,
            payload = payload,
            maxPacketBytes = 20,
        )

        assertTrue(packets.size > 20)
        assertTrue(packetizer.join(packets).contentEquals(payload))
    }

    @Test
    fun `transport reconnects once and preserves logical request`() {
        val io = FakeBlePacketIo(
            maxPacketBytes = 23,
            failFirstTransaction = true,
        ) { request ->
            request.copy(
                type = RuntimeMessageType.DEPLOY_RESULT,
                fields = mapOf("ok" to "true"),
            )
        }

        val transport = BleRuntimeTransport(io)

        val response = transport.exchange(
            RuntimeFrame(
                type = RuntimeMessageType.DEPLOY_MANIFEST,
                requestId = "deploy-1",
                fields = mapOf("payload" to "x".repeat(300)),
            )
        ).getOrThrow()

        assertEquals(RuntimeMessageType.DEPLOY_RESULT, response.type)
        assertEquals("deploy-1", response.requestId)
        assertEquals(2, io.connectCount)
        assertEquals(2, io.transactionCount)
        assertTrue(io.lastRequestPacketCount > 1)
    }

    @Test
    fun `transport blocks runtime protocol version mismatch`() {
        val io = FakeBlePacketIo { request ->
            request.copy(
                protocolVersion = 2,
                type = RuntimeMessageType.CAPABILITIES,
            )
        }

        val transport = BleRuntimeTransport(io)

        assertFailsWith<BleProtocolMismatchException> {
            transport.exchange(
                RuntimeFrame(
                    type = RuntimeMessageType.HELLO,
                    requestId = "hello",
                )
            ).getOrThrow()
        }
    }

    @Test
    fun `session handshake reads runtime capabilities`() {
        val io = FakeBlePacketIo { request ->
            request.copy(
                type = RuntimeMessageType.CAPABILITIES,
                fields = mapOf(
                    "protocol_version" to "1",
                    "runtime_version" to "0.3.0",
                    "manifest" to "true",
                    "settings" to "true",
                    "self_test" to "true",
                    "telemetry" to "true",
                    "driver_profile_families" to
                        "DHT_PULSE_SENSOR,GPIO_DIGITAL_INPUT",
                ),
            )
        }

        val session = BleRuntimeSession(BleRuntimeTransport(io))
        val capabilities = session.handshake().getOrThrow()

        assertEquals(1, capabilities.protocolVersion)
        assertEquals("0.3.0", capabilities.runtimeVersion)
        assertTrue(capabilities.manifest)
        assertTrue(capabilities.settings)
        assertTrue(capabilities.selfTest)
        assertTrue(capabilities.telemetry)
        assertEquals(
            setOf(
                "DHT_PULSE_SENSOR",
                "GPIO_DIGITAL_INPUT",
            ),
            capabilities.driverProfileFamilies,
        )
    }

    @Test
    fun `transport rejects mismatched request id`() {
        val io = FakeBlePacketIo { request ->
            request.copy(
                type = RuntimeMessageType.VALUE,
                requestId = "other-request",
            )
        }

        val transport = BleRuntimeTransport(io)

        assertFailsWith<BleRequestMismatchException> {
            transport.exchange(
                RuntimeFrame(
                    type = RuntimeMessageType.GET_VALUE,
                    requestId = "get-temp",
                    fields = mapOf("setting_id" to "temp_on"),
                )
            ).getOrThrow()
        }
    }

    private class FakeBlePacketIo(
        private val maxPacketBytes: Int = 23,
        private val failFirstTransaction: Boolean = false,
        private val responder: (RuntimeFrame) -> RuntimeFrame,
    ) : BlePacketIo {

        private val packetizer = BlePacketizer()

        override var isConnected: Boolean = false
            private set

        var connectCount = 0
            private set
        var transactionCount = 0
            private set
        var lastRequestPacketCount = 0
            private set

        override fun connect(): Result<Unit> = runCatching {
            connectCount += 1
            isConnected = true
        }

        override fun disconnect() {
            isConnected = false
        }

        override fun maxPacketBytes(): Int = maxPacketBytes

        override fun transact(
            requestPackets: List<ByteArray>,
        ): Result<List<ByteArray>> = runCatching {
            transactionCount += 1
            lastRequestPacketCount = requestPackets.size

            if (failFirstTransaction && transactionCount == 1) {
                isConnected = false
                throw BleDisconnectedException()
            }

            check(isConnected)

            val requestText = packetizer
                .join(requestPackets)
                .toString(Charsets.UTF_8)
            val request = RuntimeFrameCodec.decode(requestText)
            val response = responder(request)
            val responsePayload = RuntimeFrameCodec
                .encode(response)
                .toByteArray(Charsets.UTF_8)

            packetizer.split(
                messageId = 900,
                payload = responsePayload,
                maxPacketBytes = maxPacketBytes,
            )
        }
    }
}
