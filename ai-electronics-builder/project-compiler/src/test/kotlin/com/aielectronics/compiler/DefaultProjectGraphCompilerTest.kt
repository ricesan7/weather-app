package com.aielectronics.compiler

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultProjectGraphCompilerTest {

    private val compiler = DefaultProjectGraphCompiler()

    @Test
    fun `graph unifies hardware behavior application and runtime`() {
        val core = DesignCore(
            schemaVersion = "1.0",
            project = ProjectInfo("p1", "reference", "センサー値をスマホで確認する"),
            capabilities = setOf(CapabilityId("generated_ui")),
            board = BoardSelection(
                boardId = "xiao_esp32s3",
                transports = setOf(TransportKind.BLE),
            ),
            power = PowerPlan(emptyList(), emptyList()),
            components = listOf(
                ComponentInstance(
                    instanceId = "sensor",
                    componentId = "sensor_module",
                    role = "sensor",
                )
            ),
            connections = listOf(
                Connection(
                    id = "sensor_data",
                    from = PinRef("mcu", "D1"),
                    to = PinRef("sensor", "DATA"),
                    netType = NetType.DIGITAL,
                    wireSemantic = WireSemantic.SIGNAL,
                )
            ),
            behavior = BehaviorGraph(
                rules = listOf(
                    BehaviorRule(
                        id = "sample",
                        condition = Expression.Raw("enabled == true"),
                        actions = emptyList(),
                    )
                ),
                failsafe = emptyList(),
            ),
            settings = listOf(
                ProjectSetting(
                    id = "enabled",
                    type = SettingType.BOOLEAN,
                    defaultValue = "true",
                    mutableAtRuntime = true,
                )
            ),
            assembly = AssemblySpec(
                mode = AssemblyMode.GUIDED_SOLDER,
                stepIds = emptyList(),
            ),
            deployment = DeploymentSpec("fw", "1.0"),
            safety = SafetySummary(ValidationState.PASS, emptyList()),
        )

        val ui = UiSpec(
            listOf(
                UiPage(
                    id = "dashboard",
                    title = "状態",
                    widgets = listOf(
                        UiWidget.ValueCard(
                            id = "sensor_value",
                            binding = "telemetry.sensor_value",
                            unit = null,
                        )
                    ),
                )
            )
        )

        val bridge = DeviceBridgeSpec(
            transport = BridgeTransport.BLE,
            telemetry = listOf(
                DeviceBridgeTelemetry(
                    id = "sensor_value",
                    binding = "telemetry.sensor_value",
                    valueType = BridgeValueType.NUMBER,
                )
            ),
        )
        val software = SoftwarePlan(
            companionSoftwareRequired = true,
            base44DesignRequired = true,
            hardwareBridgeRequired = true,
            deviceBridge = bridge,
            base44Handoff = Base44HandoffSpec(
                projectId = "p1",
                projectName = "reference",
                goal = core.project.goal,
                uiSpec = ui,
                bridge = bridge,
                integration = AppHardwareIntegrationContract(
                    channels = listOf(
                        AppBridgeChannel(
                            id = "sensor_value",
                            binding = "telemetry.sensor_value",
                            direction = AppBridgeDirection.HARDWARE_TO_BASE44,
                            valueType = BridgeValueType.NUMBER,
                        )
                    )
                ),
            ),
        )

        val graph = compiler.compile(core, ui, software).getOrThrow()

        assertTrue(graph.nodes.any { it.domain == ProjectGraphDomain.HARDWARE })
        assertTrue(graph.nodes.any { it.domain == ProjectGraphDomain.BEHAVIOR })
        assertTrue(graph.nodes.any { it.domain == ProjectGraphDomain.APPLICATION })
        assertTrue(graph.nodes.any { it.domain == ProjectGraphDomain.RUNTIME })
        assertTrue(
            graph.edges.any {
                it.kind == ProjectGraphEdgeKind.TELEMETRY_TO_APP &&
                    it.label == "telemetry.sensor_value"
            }
        )
    }
}
