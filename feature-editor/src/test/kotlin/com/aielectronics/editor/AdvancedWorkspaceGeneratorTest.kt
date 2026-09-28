package com.aielectronics.editor

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AdvancedWorkspaceGeneratorTest {

    @Test
    fun `generated workspace contains editable source manifest and protected hardware reference`() {
        val board = BoardSelection(
            boardId = "xiao_esp32s3",
            transports = setOf(TransportKind.BLE),
        )
        val power = PowerPlan(
            domains = emptyList(),
            sources = emptyList(),
        )
        val setting = ProjectSetting(
            id = "temp_on",
            type = SettingType.NUMBER,
            defaultValue = "30.0",
            mutableAtRuntime = true,
        )
        val design = DesignIr(
            schemaVersion = "1.0",
            project = ProjectInfo(
                id = "advanced_fixture",
                name = "Advanced Fixture",
                goal = "temperature fan",
            ),
            capabilities = emptySet(),
            board = board,
            power = power,
            components = emptyList(),
            connections = emptyList(),
            behavior = BehaviorGraph(
                rules = listOf(
                    BehaviorRule(
                        id = "auto_on",
                        condition = Expression.Raw("temperature >= temp_on"),
                        actions = listOf(Action.SetOutput("fan", "ON")),
                    )
                ),
                failsafe = emptyList(),
            ),
            settings = listOf(setting),
            ui = UiSpec(emptyList()),
            tests = emptyList(),
            diagnostics = emptyList(),
            assembly = AssemblySpec(
                mode = AssemblyMode.ADVANCED_FREEFORM,
                stepIds = emptyList(),
            ),
            deployment = DeploymentSpec(
                firmwareProfileId = "fw_beginner_runtime",
                manifestVersion = "1.0",
            ),
            safety = SafetySummary(
                state = ValidationState.PASS,
                issues = emptyList(),
            ),
        )
        val manifest = ProjectManifest(
            version = "1.0",
            projectId = "advanced_fixture",
            boardId = "xiao_esp32s3",
            drivers = emptyList(),
            buses = emptyList(),
            gpio = emptyList(),
            devices = emptyList(),
            rules = design.behavior.rules,
            settings = listOf(setting),
            interlocks = emptyList(),
            failsafe = emptyList(),
            telemetryIds = emptyList(),
            tests = emptyList(),
            minimumRuntimeVersion = "1.0.0",
        )
        val graph = CircuitGraph(
            board = board,
            components = emptyList(),
            power = power,
            connections = emptyList(),
        )
        val bundle = ReleaseBundle(
            designIr = design,
            validation = ValidationReport(
                state = ValidationState.PASS,
                issues = emptyList(),
            ),
            circuitGraph = graph,
            diagramSpec = DiagramSpec(emptyList()),
            manifest = manifest,
            uiSpec = UiSpec(emptyList()),
            testPlan = TestPlan(emptyList()),
        )

        val workspace = AdvancedWorkspaceGenerator().generate(bundle)

        assertEquals("advanced_fixture", workspace.projectId)
        assertTrue(workspace.files.any { it.path == "src/project.cpp" && !it.readOnly })
        assertTrue(workspace.files.any { it.path == "runtime/project.manifest" && !it.readOnly })
        assertTrue(workspace.files.any { it.path == "hardware/netlist.txt" && it.readOnly })
        assertTrue(
            workspace.files
                .single { it.path == "runtime/project.manifest" }
                .workingContent
                .contains("meta\tboard\txiao_esp32s3")
        )
        assertFalse(workspace.files.first().modified)
    }
}
