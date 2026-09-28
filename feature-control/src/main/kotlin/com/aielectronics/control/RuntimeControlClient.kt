package com.aielectronics.control

import com.aielectronics.core.model.UiSpec
import com.aielectronics.runtime.RuntimeFrame
import com.aielectronics.runtime.RuntimeMessageType
import com.aielectronics.runtime.RuntimeTransport
import java.util.concurrent.atomic.AtomicLong

class RuntimeOperationException(
    val code: String,
    override val message: String,
) : RuntimeException(message)

class RuntimeControlClient(
    private val transport: RuntimeTransport,
) {
    private val requestCounter = AtomicLong(1)

    fun telemetry(): Map<String, String> {
        val response = exchange(
            type = RuntimeMessageType.TELEMETRY,
        )
        requireType(response, RuntimeMessageType.TELEMETRY)
        return response.fields
    }

    fun getSetting(settingId: String): String {
        val response = exchange(
            type = RuntimeMessageType.GET_VALUE,
            fields = mapOf("setting_id" to settingId),
        )
        requireType(response, RuntimeMessageType.VALUE)
        return response.fields["value"]
            ?: error("Runtime VALUE response has no value")
    }

    fun loadSettings(uiSpec: UiSpec): Map<String, String> =
        DashboardProjection
            .mutableSettingIds(uiSpec)
            .associateWith(::getSetting)

    fun setSetting(
        settingId: String,
        value: String,
    ): String {
        val response = exchange(
            type = RuntimeMessageType.SET_VALUE,
            fields = mapOf(
                "setting_id" to settingId,
                "value" to value,
            ),
        )
        requireType(response, RuntimeMessageType.VALUE)
        return response.fields["value"]
            ?: value
    }

    fun runTest(testId: String): Boolean {
        val response = exchange(
            type = RuntimeMessageType.RUN_TEST,
            fields = mapOf("test_id" to testId),
        )
        requireType(response, RuntimeMessageType.TEST_RESULT)
        return response.fields["passed"] == "true"
    }

    private fun exchange(
        type: RuntimeMessageType,
        fields: Map<String, String> = emptyMap(),
    ): RuntimeFrame {
        val request = RuntimeFrame(
            type = type,
            requestId = nextRequestId(type),
            fields = fields,
        )
        val response = transport.exchange(request).getOrThrow()

        if (response.type == RuntimeMessageType.ERROR) {
            throw RuntimeOperationException(
                code = response.fields["code"] ?: "E_RUNTIME",
                message = response.fields["message"] ?: "装置との通信に失敗しました。",
            )
        }

        require(response.requestId == request.requestId) {
            "Runtime response requestId mismatch"
        }
        return response
    }

    private fun requireType(
        response: RuntimeFrame,
        expected: RuntimeMessageType,
    ) {
        require(response.type == expected) {
            "Expected $expected but received ${response.type}"
        }
    }

    private fun nextRequestId(type: RuntimeMessageType): String =
        type.name.lowercase() + "-" + requestCounter.getAndIncrement()
}
