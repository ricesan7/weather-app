package com.aielectronics.compiler

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DefaultProjectCompilerTest {

    @Test
    fun `compiler stops immediately on electrical BLOCK`() {
        var behaviorCalled = false

        val compiler = compiler(
            validator = makeValidator {
                ValidationReport(
                    state = ValidationState.BLOCKED,
                    issues = listOf(
                        ValidationIssue(
                            code = "E_POWER_VOLTAGE_MISMATCH",
                            severity = Severity.CRITICAL,
                            message = "Voltage mismatch",
                        )
                    ),
                )
            },
            behavior = object : BehaviorCompiler {
                override fun compile(
                    requirements: ResolvedRequirements,
                    capabilities: CapabilitySet,
                ): Result<BehaviorGraph> {
                    behaviorCalled = true
                    return Result.success(BehaviorGraph(failsafe = emptyList()))
                }
            },
        )

        val result = compiler.compile(requirements())

        assertIs<CompileResult.Blocked>(result)
        assertEquals(false, behaviorCalled)
    }

    @Test
    fun `successful pipeline returns one consistent release bundle`() {
        val compiler = compiler()

        val success = assertIs<CompileResult.Success>(compiler.compile(requirements()))
        assertEquals("xiao_esp32s3", success.bundle.designIr.board.boardId)
        assertEquals("reference", success.bundle.designIr.project.name)
        assertEquals(ValidationState.PASS, success.bundle.validation.state)
        assertEquals("1.0", success.bundle.manifest?.version)
    }

    private fun requirements() = ResolvedRequirements(
        goal = "温度を監視する",
        slots = mapOf(
            "project_name" to RequirementValue("reference", RequirementSource.USER, 1.0),
            "req_installation" to RequirementValue("guided_solder", RequirementSource.INFERRED_SAFE_DEFAULT, 0.9),
        ),
    )

    private fun compiler(
        validator: ElectricalValidator = makeValidator {
            ValidationReport(ValidationState.PASS, emptyList())
        },
        behavior: BehaviorCompiler = object : BehaviorCompiler {
            override fun compile(
                requirements: ResolvedRequirements,
                capabilities: CapabilitySet,
            ): Result<BehaviorGraph> =
                Result.success(BehaviorGraph(failsafe = emptyList()))
        },
    ): DefaultProjectCompiler {
        val board = BoardSelection("xiao_esp32s3", setOf(TransportKind.BLE))
        val components = ResolvedComponents(
            listOf(ComponentInstance("mcu", "xiao_esp32s3", "controller"))
        )
        val power = PowerPlan(
            domains = listOf(PowerDomain("logic", 3.3, 100.0, setOf("mcu"))),
            sources = listOf(PowerSource("usb", 5.0, 500.0, Polarity.NOT_APPLICABLE)),
        )

        return DefaultProjectCompiler(
            capabilityMapper = object : CapabilityMapper {
                override fun map(requirements: ResolvedRequirements) =
                    CapabilitySet(setOf(CapabilityId("monitor")))
            },
            componentResolver = object : ComponentResolver {
                override fun resolve(
                    capabilities: CapabilitySet,
                    requirements: ResolvedRequirements,
                ) = Result.success(components)
            },
            boardSelector = object : BoardSelector {
                override fun select(
                    capabilities: CapabilitySet,
                    components: ResolvedComponents,
                    requirements: ResolvedRequirements,
                ) = Result.success(board)
            },
            powerPlanner = object : PowerPlanner {
                override fun plan(
                    components: ResolvedComponents,
                    requirements: ResolvedRequirements,
                ) = Result.success(power)
            },
            pinAllocator = object : PinAllocator {
                override fun allocate(
                    board: BoardSelection,
                    components: ResolvedComponents,
                ) = Result.success(emptyList<PinAssignment>())
            },
            circuitCompiler = object : CircuitCompiler {
                override fun compile(
                    board: BoardSelection,
                    components: ResolvedComponents,
                    power: PowerPlan,
                    pins: List<PinAssignment>,
                ) = Result.success(CircuitGraph(board, components.components, power, emptyList()))
            },
            electricalValidator = validator,
            behaviorCompiler = behavior,
            coreAssembler = DefaultDesignCoreAssembler(),
            diagramCompiler = object : DiagramCompiler {
                override fun compile(graph: CircuitGraph) =
                    Result.success(
                        DiagramSpec(
                            listOf(DiagramView("overview", DiagramViewKind.SYSTEM_OVERVIEW))
                        )
                    )
            },
            manifestCompiler = object : ManifestCompiler {
                override fun compile(core: DesignCore) =
                    Result.success(
                        ProjectManifest(
                            version = "1.0",
                            projectId = core.project.id,
                            boardId = core.board.boardId,
                            drivers = emptyList(),
                            buses = emptyList(),
                            gpio = emptyList(),
                            devices = emptyList(),
                            rules = core.behavior.rules,
                            settings = core.settings,
                            interlocks = core.behavior.interlocks,
                            failsafe = core.behavior.failsafe,
                            telemetryIds = emptyList(),
                            tests = emptyList(),
                            minimumRuntimeVersion = "1.0.0",
                        )
                    )
            },
            uiCompiler = object : UiCompiler {
                override fun compile(core: DesignCore) = Result.success(UiSpec(emptyList()))
            },
            diagnosticCompiler = object : DiagnosticCompiler {
                override fun compile(core: DesignCore) =
                    Result.success(DiagnosticBundle(TestPlan(emptyList()), emptyList()))
            },
        )
    }

    private fun makeValidator(block: (CircuitGraph) -> ValidationReport) =
        object : ElectricalValidator {
            override fun validate(graph: CircuitGraph): ValidationReport = block(graph)
        }
}
