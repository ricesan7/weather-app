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
    fun `smartphone request creates Base44 handoff and hardware bridge contract`() {
        val core = core(
            goal = "温度を監視してスマホから設定温度を変更したい",
            logging = LoggingSpec(
                channelIds = listOf("temperature"),
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
                            id = "temperature",
                            binding = "telemetry.temperature",
                            unit = "°C",
                        ),
                        UiWidget.Slider(
                            id = "temp_on",
                            binding = "settings.temp_on",
                            min = 0.0,
                            max = 60.0,
                            step = 0.5,
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

        assertTrue(plan.companionSoftwareRequired)
        assertTrue(plan.base44DesignRequired)
        assertTrue(plan.hardwareBridgeRequired)
        assertEquals(BridgeTransport.BLE, plan.deviceBridge?.transport)
        assertEquals("settings.temp_on", plan.deviceBridge?.commands?.single()?.binding)
        assertEquals("telemetry.temperature", plan.deviceBridge?.telemetry?.single()?.binding)
        assertNotNull(plan.base44Handoff)
    }

    @Test
    fun `hardware only request does not generate Base44 app`() {
        val core = core(
            goal = "温度が30度以上ならファンを回す",
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
        capabilities = setOf(
            CapabilityId("measure_temperature"),
            CapabilityId("generated_ui"),
        ),
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
