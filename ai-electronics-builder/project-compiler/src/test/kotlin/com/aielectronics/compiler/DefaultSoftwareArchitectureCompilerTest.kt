package com.aielectronics.compiler

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DefaultSoftwareArchitectureCompilerTest {

    private val compiler = DefaultSoftwareArchitectureCompiler()

    @Test
    fun `smartphone software creates generic Base44 hardware integration contract`() {
        val core = core(
            goal = "センサー値をスマホで確認して設定値も変更したい",
            logging = LoggingSpec(
                channelIds = listOf("sensor_value"),
                intervalSeconds = 60,
                retentionDays = 7,
                primaryStorage = StorageTarget.PHONE,
            ),
        )
        val ui = UiSpec(
            pages = listOf(
                UiPage(
                    id = "dashboard",
                    title = "状態",
                    widgets = listOf(
                        UiWidget.ValueCard(
                            id = "sensor_value",
                            binding = "telemetry.sensor_value",
                            unit = null,
                        ),
                        UiWidget.Slider(
                            id = "target_value",
                            binding = "settings.target_value",
                            min = 0.0,
                            max = 100.0,
                            step = 1.0,
                        ),
                    ),
                )
            )
        )

        val plan = compiler.compile(
            requirements = requirements(core.project.goal),
            core = core,
            ui = ui,
        ).getOrThrow()

        val handoff = assertNotNull(plan.base44Handoff)
        assertTrue(plan.companionSoftwareRequired)
        assertTrue(plan.base44DesignRequired)
        assertTrue(plan.hardwareBridgeRequired)
        assertEquals(BridgeTransport.BLE, plan.deviceBridge?.transport)

        assertTrue(
            handoff.integration.channels.any {
                it.binding == "telemetry.sensor_value" &&
                    it.direction == AppBridgeDirection.HARDWARE_TO_BASE44
            }
        )
        assertTrue(
            handoff.integration.channels.any {
                it.binding == "settings.target_value" &&
                    it.direction == AppBridgeDirection.BASE44_TO_HARDWARE
            }
        )
        assertTrue(
            Base44ApplicationCapability.LIVE_DATA in handoff.requestedCapabilities
        )
        val telemetryChannel = handoff.integration.channels.first {
            it.binding == "telemetry.sensor_value"
        }
        assertEquals(
            AppBridgePresentation.VALUE,
            telemetryChannel.presentation,
        )
        val settingChannel = handoff.integration.channels.first {
            it.binding == "settings.target_value"
        }
        assertEquals(
            AppBridgePresentation.SLIDER,
            settingChannel.presentation,
        )
        assertTrue(
            Base44ApplicationCapability.DEVICE_SETTINGS in handoff.requestedCapabilities
        )
        val dashboard = handoff.integration.pages.first {
            it.id == "dashboard"
        }
        assertEquals("状態", dashboard.title)
        assertEquals(
            listOf("telemetry.sensor_value", "settings.target_value"),
            dashboard.widgets.sortedBy { it.order }.map { it.binding },
        )
        assertEquals(
            AppBridgeWidgetSpan.THIRD,
            dashboard.widgets.first { it.binding == "telemetry.sensor_value" }.span,
        )
    }

    @Test
    fun `notification is a generic Base44 capability not a device specific rule`() {
        val core = core(
            goal = "センサーの状態をスマホに表示して条件に応じて通知したい",
            logging = null,
        )
        val ui = UiSpec(
            listOf(
                UiPage(
                    id = "dashboard",
                    title = "状態",
                    widgets = listOf(
                        UiWidget.Status(
                            id = "device_state",
                            binding = "telemetry.device_state",
                        )
                    ),
                )
            )
        )

        val plan = compiler.compile(
            requirements = requirements(core.project.goal),
            core = core,
            ui = ui,
        ).getOrThrow()

        val handoff = assertNotNull(plan.base44Handoff)
        assertTrue(
            Base44ApplicationCapability.NOTIFICATIONS in handoff.requestedCapabilities
        )
        assertTrue(
            handoff.integration.channels.any {
                it.binding == "telemetry.device_state" &&
                    it.direction == AppBridgeDirection.HARDWARE_TO_BASE44
            }
        )
    }

    @Test
    fun `hardware only request does not generate Base44 app`() {
        val core = core(
            goal = "センサー値に応じて装置を自動制御する",
            logging = null,
        )

        val plan = compiler.compile(
            requirements = requirements(core.project.goal),
            core = core,
            ui = UiSpec(emptyList()),
        ).getOrThrow()

        assertFalse(plan.companionSoftwareRequired)
        assertFalse(plan.base44DesignRequired)
        assertFalse(plan.hardwareBridgeRequired)
        assertEquals(null, plan.deviceBridge)
        assertEquals(null, plan.base44Handoff)
    }

    private fun requirements(goal: String) = ResolvedRequirements(
        goal = goal,
        slots = emptyMap(),
    )

    private fun core(
        goal: String,
        logging: LoggingSpec?,
    ) = DesignCore(
        schemaVersion = "1.0",
        project = ProjectInfo("project-1", "reference", goal),
        capabilities = setOf(CapabilityId("generated_ui")),
        board = BoardSelection(
            boardId = "xiao_esp32s3",
            transports = setOf(TransportKind.BLE),
        ),
        power = PowerPlan(emptyList(), emptyList()),
        components = emptyList(),
        connections = emptyList(),
        behavior = BehaviorGraph(failsafe = emptyList()),
        logging = logging,
        assembly = AssemblySpec(
            mode = AssemblyMode.GUIDED_SOLDER,
            stepIds = emptyList(),
        ),
        deployment = DeploymentSpec(
            firmwareProfileId = "default",
            manifestVersion = "1.0",
        ),
        safety = SafetySummary(
            state = ValidationState.PASS,
            issues = emptyList(),
        ),
    )
}
