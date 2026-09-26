package com.aielectronics.runtime

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuntimeProtocolTest {

    @Test
    fun `frame codec round trips escaped values`() {
        val frame = RuntimeFrame(
            type = RuntimeMessageType.SET_VALUE,
            requestId = "req|1",
            fields = mapOf(
                "setting_id" to "temp=on",
                "value" to "30|31\n32",
            ),
        )

        val decoded = RuntimeFrameCodec.decode(RuntimeFrameCodec.encode(frame))

        assertEquals(frame, decoded)
    }

    @Test
    fun `planner selects manifest only and runtime install automatically`() {
        val bundle = bundle()
        val plan = DefaultDeploymentPlanner().plan(
            bundle,
            DeviceInfo(
                deviceId = "dev1",
                boardId = "xiao_esp32s3",
                runtimeVersion = "0.9.0",
                transports = setOf("BLE"),
            ),
        ).getOrThrow()

        assertEquals(DeployStrategy.MANIFEST_ONLY, plan.strategy)
        assertTrue(plan.requiresRuntimeInstall)
    }

    @Test
    fun `one action deploy reaches ready and runs required tests`() {
        val transport = FakeTransport()
        val progress = mutableListOf<DeployProgress>()

        val manager = DefaultDeployManager(
            clientFactory = { RuntimeClient(transport) },
            runtimeInstaller = object : RuntimeInstaller {
                override fun ensureInstalled(
                    device: DeviceInfo,
                    minimumVersion: String,
                ): Result<DeviceInfo> =
                    Result.success(device.copy(runtimeVersion = minimumVersion))
            },
        )

        val plan = DefaultDeploymentPlanner().plan(
            bundle(),
            DeviceInfo(
                deviceId = "dev1",
                boardId = "xiao_esp32s3",
                runtimeVersion = null,
                transports = setOf("BLE"),
            ),
        ).getOrThrow()

        val result = manager.deploy(plan) { progress += it }

        assertIs<DeployResult.Success>(result)
        assertEquals(DeployState.READY, progress.last().state)
        assertEquals(100, progress.last().percent)
        assertTrue(transport.received.any { it.type == RuntimeMessageType.DEPLOY_MANIFEST })
        assertTrue(transport.received.any { it.type == RuntimeMessageType.VERIFY_PROJECT })
        assertTrue(transport.received.count { it.type == RuntimeMessageType.RUN_TEST } == 2)
    }

    @Test
    fun `runtime setting changes without deploy or reflash`() {
        val transport = FakeTransport()
        val service = DefaultRuntimeSettingsService(RuntimeClient(transport))

        val result = service.set(RuntimeSettingValue("temp_on", "32.0"))

        val success = assertIs<SettingUpdateResult.Success>(result)
        assertEquals("32.0", success.ack.appliedValue)
        assertTrue(success.ack.persisted)
        assertTrue(transport.received.none {
            it.type == RuntimeMessageType.DEPLOY_MANIFEST
        })
    }

    private fun bundle(): ReleaseBundle {
        val tests = listOf(
            TestSpec("sensor_probe", "センサー確認", "probe_required_sensors", true),
            TestSpec("fan_output_test", "ファン確認", "fan_on_1s_then_off", true),
        )
        val manifest = ProjectManifest(
            version = "1.0",
            projectId = "reference",
            boardId = "xiao_esp32s3",
            drivers = listOf("sht31", "gpio_switch"),
            buses = listOf(
                ManifestBus(
                    id = "i2c0",
                    kind = "I2C",
                    pins = mapOf(
                        "SDA" to "pin_xiao_d4_gpio5",
                        "SCL" to "pin_xiao_d5_gpio6",
                    ),
                )
            ),
            gpio = listOf(ManifestGpio("pin_xiao_d3_gpio4", "OUTPUT", "LOW")),
            devices = emptyList(),
            rules = emptyList(),
            settings = listOf(
                ProjectSetting("temp_on", SettingType.NUMBER, "30.0", true)
            ),
            interlocks = emptyList(),
            failsafe = emptyList(),
            telemetryIds = listOf("temperature", "fan_state"),
            tests = tests,
            minimumRuntimeVersion = "1.0.0",
        )
        val board = BoardSelection("xiao_esp32s3", setOf(TransportKind.BLE))
        val graph = CircuitGraph(
            board = board,
            components = emptyList(),
            power = PowerPlan(emptyList(), emptyList()),
            connections = emptyList(),
        )
        val ir = DesignIr(
            schemaVersion = "1.0",
            project = ProjectInfo("reference", "reference", "reference"),
            capabilities = emptySet(),
            board = board,
            power = graph.power,
            components = emptyList(),
            connections = emptyList(),
            behavior = BehaviorGraph(failsafe = emptyList()),
            ui = UiSpec(emptyList()),
            tests = tests,
            diagnostics = emptyList(),
            assembly = AssemblySpec(AssemblyMode.GUIDED_SOLDER, emptyList()),
            deployment = DeploymentSpec("fw_beginner_runtime", "1.0"),
            safety = SafetySummary(ValidationState.PASS, emptyList()),
        )
        return ReleaseBundle(
            designIr = ir,
            validation = ValidationReport(ValidationState.PASS, emptyList()),
            circuitGraph = graph,
            diagramSpec = DiagramSpec(emptyList()),
            manifest = manifest,
            uiSpec = UiSpec(emptyList()),
            testPlan = TestPlan(tests),
        )
    }

    private class FakeTransport : RuntimeTransport {
        val received = mutableListOf<RuntimeFrame>()
        private val settings = mutableMapOf<String, String>()

        override fun exchange(frame: RuntimeFrame): Result<RuntimeFrame> = runCatching {
            received += frame
            when (frame.type) {
                RuntimeMessageType.DEPLOY_MANIFEST -> RuntimeFrame(
                    type = RuntimeMessageType.DEPLOY_RESULT,
                    requestId = frame.requestId,
                    fields = mapOf("ok" to "true"),
                )

                RuntimeMessageType.VERIFY_PROJECT -> RuntimeFrame(
                    type = RuntimeMessageType.VERIFY_RESULT,
                    requestId = frame.requestId,
                    fields = mapOf("ok" to "true"),
                )

                RuntimeMessageType.RUN_TEST -> RuntimeFrame(
                    type = RuntimeMessageType.TEST_RESULT,
                    requestId = frame.requestId,
                    fields = mapOf("passed" to "true"),
                )

                RuntimeMessageType.SET_VALUE -> {
                    val id = frame.fields.getValue("setting_id")
                    val value = frame.fields.getValue("value")
                    settings[id] = value
                    RuntimeFrame(
                        type = RuntimeMessageType.VALUE,
                        requestId = frame.requestId,
                        fields = mapOf(
                            "setting_id" to id,
                            "value" to value,
                            "persisted" to "true",
                        ),
                    )
                }

                RuntimeMessageType.GET_VALUE -> {
                    val id = frame.fields.getValue("setting_id")
                    RuntimeFrame(
                        type = RuntimeMessageType.VALUE,
                        requestId = frame.requestId,
                        fields = mapOf(
                            "setting_id" to id,
                            "value" to settings[id].orEmpty(),
                        ),
                    )
                }

                else -> error("Unexpected request type " + frame.type)
            }
        }
    }
}
