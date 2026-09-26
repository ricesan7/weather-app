package com.aielectronics.control

import com.aielectronics.core.model.UiPage
import com.aielectronics.core.model.UiSpec
import com.aielectronics.core.model.UiWidget
import com.aielectronics.runtime.RuntimeFrame
import com.aielectronics.runtime.RuntimeMessageType
import com.aielectronics.runtime.RuntimeTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RuntimeControlClientTest {

    @Test
    fun `loads telemetry and settings through runtime protocol`() {
        val transport = FakeRuntimeTransport()
        val client = RuntimeControlClient(transport)

        val ui = UiSpec(
            pages = listOf(
                UiPage(
                    id = "dashboard",
                    title = "状態",
                    widgets = listOf(
                        UiWidget.Select(
                            id = "mode",
                            binding = "settings.mode",
                            options = listOf("AUTO", "MANUAL"),
                        ),
                        UiWidget.Toggle(
                            id = "manual_fan",
                            binding = "settings.manual_fan",
                        ),
                    ),
                )
            )
        )

        val telemetry = client.telemetry()
        val settings = client.loadSettings(ui)

        assertEquals("31.5", telemetry["temperature"])
        assertEquals("ON", telemetry["fan_state"])
        assertEquals("AUTO", settings["mode"])
        assertEquals("false", settings["manual_fan"])

        assertTrue(
            transport.requests.any {
                it.type == RuntimeMessageType.TELEMETRY
            }
        )
        assertEquals(
            setOf("mode", "manual_fan"),
            transport.requests
                .filter { it.type == RuntimeMessageType.GET_VALUE }
                .mapNotNull { it.fields["setting_id"] }
                .toSet(),
        )
    }

    @Test
    fun `setting mutation always uses SET_VALUE`() {
        val transport = FakeRuntimeTransport()
        val client = RuntimeControlClient(transport)

        val accepted = client.setSetting(
            settingId = "temp_on",
            value = "32.5",
        )

        assertEquals("32.5", accepted)
        val request = transport.requests.last()
        assertEquals(RuntimeMessageType.SET_VALUE, request.type)
        assertEquals("temp_on", request.fields["setting_id"])
        assertEquals("32.5", request.fields["value"])
    }

    @Test
    fun `diagnostic action uses RUN_TEST`() {
        val transport = FakeRuntimeTransport()
        val client = RuntimeControlClient(transport)

        assertTrue(client.runTest("sensor_probe"))

        val request = transport.requests.last()
        assertEquals(RuntimeMessageType.RUN_TEST, request.type)
        assertEquals("sensor_probe", request.fields["test_id"])
    }

    private class FakeRuntimeTransport : RuntimeTransport {
        val requests = mutableListOf<RuntimeFrame>()

        private val settings = mutableMapOf(
            "mode" to "AUTO",
            "manual_fan" to "false",
            "temp_on" to "30.0",
        )

        override fun exchange(frame: RuntimeFrame): Result<RuntimeFrame> = runCatching {
            requests += frame

            when (frame.type) {
                RuntimeMessageType.TELEMETRY -> frame.copy(
                    type = RuntimeMessageType.TELEMETRY,
                    fields = mapOf(
                        "temperature" to "31.5",
                        "humidity" to "72",
                        "fan_state" to "ON",
                    ),
                )

                RuntimeMessageType.GET_VALUE -> {
                    val id = requireNotNull(frame.fields["setting_id"])
                    frame.copy(
                        type = RuntimeMessageType.VALUE,
                        fields = mapOf(
                            "setting_id" to id,
                            "value" to requireNotNull(settings[id]),
                        ),
                    )
                }

                RuntimeMessageType.SET_VALUE -> {
                    val id = requireNotNull(frame.fields["setting_id"])
                    val value = requireNotNull(frame.fields["value"])
                    settings[id] = value
                    frame.copy(
                        type = RuntimeMessageType.VALUE,
                        fields = mapOf(
                            "setting_id" to id,
                            "value" to value,
                        ),
                    )
                }

                RuntimeMessageType.RUN_TEST -> frame.copy(
                    type = RuntimeMessageType.TEST_RESULT,
                    fields = mapOf(
                        "test_id" to requireNotNull(frame.fields["test_id"]),
                        "passed" to "true",
                    ),
                )

                else -> frame.copy(
                    type = RuntimeMessageType.ERROR,
                    fields = mapOf(
                        "code" to "E_UNSUPPORTED",
                        "message" to "unsupported test request",
                    ),
                )
            }
        }
    }
}
