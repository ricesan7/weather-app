package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultProjectCompiler(
    private val capabilityMapper: CapabilityMapper,
    private val componentResolver: ComponentResolver,
    private val boardSelector: BoardSelector,
    private val powerPlanner: PowerPlanner,
    private val pinAllocator: PinAllocator,
    private val circuitCompiler: CircuitCompiler,
    private val electricalValidator: ElectricalValidator,
    private val behaviorCompiler: BehaviorCompiler,
    private val coreAssembler: DesignCoreAssembler,
    private val diagramCompiler: DiagramCompiler,
    private val manifestCompiler: ManifestCompiler,
    private val uiCompiler: UiCompiler,
    private val diagnosticCompiler: DiagnosticCompiler,
) : ProjectCompiler {

    override fun compile(requirements: ResolvedRequirements): CompileResult {
        val blockingRequirements = requirements.unresolved.filter { it.blocking }
        if (blockingRequirements.isNotEmpty()) {
            return CompileResult.NeedUserInput(blockingRequirements)
        }

        val capabilities = capabilityMapper.map(requirements)

        val components = componentResolver.resolve(capabilities, requirements)
            .getOrElse { return failed("component_resolve", it) }

        val board = boardSelector.select(capabilities, components, requirements)
            .getOrElse { return failed("board_select", it) }

        val power = powerPlanner.plan(components, requirements)
            .getOrElse { return failed("power_plan", it) }

        val pins = pinAllocator.allocate(board, components)
            .getOrElse { return failed("pin_allocate", it) }

        val circuitGraph = circuitCompiler.compile(board, components, power, pins)
            .getOrElse { return failed("circuit_compile", it) }

        val validation = electricalValidator.validate(circuitGraph)
        if (validation.isBlocked) {
            return CompileResult.Blocked(validation)
        }

        val behavior = behaviorCompiler.compile(requirements, capabilities)
            .getOrElse { return failed("behavior_compile", it) }

        val core = coreAssembler.assemble(
            requirements = requirements,
            capabilities = capabilities,
            board = board,
            components = components,
            circuitGraph = circuitGraph,
            behavior = behavior,
            validation = validation,
        ).getOrElse { return failed("design_core_assemble", it) }

        val diagrams = diagramCompiler.compile(circuitGraph)
            .getOrElse { return failed("diagram_compile", it) }

        val manifest = manifestCompiler.compile(core)
            .getOrElse { return failed("manifest_compile", it) }

        val ui = uiCompiler.compile(core)
            .getOrElse { return failed("ui_compile", it) }

        val diagnostics = diagnosticCompiler.compile(core)
            .getOrElse { return failed("diagnostic_compile", it) }

        val finalIr = core.finalize(
            ui = ui,
            tests = diagnostics.testPlan.tests,
            diagnostics = diagnostics.diagnostics,
        )

        return CompileResult.Success(
            ReleaseBundle(
                designIr = finalIr,
                validation = validation,
                circuitGraph = circuitGraph,
                diagramSpec = diagrams,
                manifest = manifest,
                uiSpec = ui,
                testPlan = diagnostics.testPlan,
            )
        )
    }

    private fun failed(stage: String, throwable: Throwable): CompileResult.Failed =
        CompileResult.Failed(
            CompileFailure(
                stage = stage,
                message = throwable.message ?: throwable::class.simpleName.orEmpty(),
                causeCode = throwable::class.simpleName,
            )
        )
}
