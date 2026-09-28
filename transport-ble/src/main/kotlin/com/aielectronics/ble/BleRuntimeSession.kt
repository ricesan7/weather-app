package com.aielectronics.ble

import com.aielectronics.runtime.RuntimeFrame
import com.aielectronics.runtime.RuntimeMessageType
import com.aielectronics.runtime.RuntimeTransport

data class RuntimeCapabilities(
    val protocolVersion: Int,
    val manifest: Boolean,
    val settings: Boolean,
    val selfTest: Boolean,
    val telemetry: Boolean,
)

class BleRuntimeSession(
    private val transport: RuntimeTransport,
) {

    fun handshake(): Result<RuntimeCapabilities> = runCatching {
        val response = transport.exchange(
            RuntimeFrame(
                type = RuntimeMessageType.HELLO,
                requestId = "hello",
            )
        ).getOrThrow()

        require(response.type == RuntimeMessageType.CAPABILITIES) {
            "Expected CAPABILITIES"
        }

        RuntimeCapabilities(
            protocolVersion = response.fields["protocol_version"]
                ?.toIntOrNull()
                ?: response.protocolVersion,
            manifest = response.fields["manifest"] == "true",
            settings = response.fields["settings"] == "true",
            selfTest = response.fields["self_test"] == "true",
            telemetry = response.fields["telemetry"] == "true",
        )
    }
}
