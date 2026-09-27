package com.aielectronics.compiler

import com.aielectronics.core.model.*

sealed interface CompileResult {
    data class Success(val bundle: ReleaseBundle) : CompileResult
    data class NeedUserInput(val questions: List<MissingRequirement>) : CompileResult
    data class Blocked(val report: ValidationReport) : CompileResult
    data class Failed(val error: CompileFailure) : CompileResult
}

data class CompileFailure(val stage: String, val message: String, val causeCode: String? = null)

interface ProjectCompiler {
    fun compile(requirements: ResolvedRequirements): CompileResult
}

interface RequirementResolver {
    fun resolve(intent: IntentDraft, existing: ResolvedRequirements? = null): RequirementResolution
}

sealed interface RequirementResolution {
    data class Ready(val requirements: ResolvedRequirements) : RequirementResolution
    data class NeedUserInput(val missing: List<MissingRequirement>) : RequirementResolution
}

interface CapabilityMapper {
    fun map(requirements: ResolvedRequirements): CapabilitySet
}

interface ComponentResolver {
    fun resolve(capabilities: CapabilitySet, requirements: ResolvedRequirements): Result<ResolvedComponents>
}

interface BoardSelector {
    fun select(
        capabilities: CapabilitySet,
        components: ResolvedComponents,
        requirements: ResolvedRequirements,
    ): Result<BoardSelection>
}

interface PowerPlanner {
    fun plan(
        board: BoardSelection,
        components: ResolvedComponents,
        requirements: ResolvedRequirements,
    ): Result<PowerPlan>
}

interface PinAllocator {
    fun allocate(board: BoardSelection, components: ResolvedComponents): Result<List<PinAssignment>>
}

interface CircuitCompiler {
    fun compile(
        board: BoardSelection,
        components: ResolvedComponents,
        power: PowerPlan,
        pins: List<PinAssignment>,
    ): Result<CircuitGraph>
}

interface ElectricalValidator {
    fun validate(graph: CircuitGraph): ValidationReport
}

interface BehaviorCompiler {
    fun compile(
        requirements: ResolvedRequirements,
        capabilities: CapabilitySet,
    ): Result<BehaviorCompilation>
}

interface DesignCoreAssembler {
    fun assemble(
        requirements: ResolvedRequirements,
        capabilities: CapabilitySet,
        board: BoardSelection,
        components: ResolvedComponents,
        circuitGraph: CircuitGraph,
        behavior: BehaviorCompilation,
        validation: ValidationReport,
    ): Result<DesignCore>
}

interface ManifestCompiler {
    fun compile(core: DesignCore): Result<ProjectManifest>
}

interface UiCompiler {
    fun compile(core: DesignCore): Result<UiSpec>
}

interface DiagramCompiler {
    fun compile(graph: CircuitGraph): Result<DiagramSpec>
}

interface DiagnosticCompiler {
    fun compile(core: DesignCore): Result<DiagnosticBundle>
}

interface SoftwareArchitectureCompiler {
    fun compile(
        requirements: ResolvedRequirements,
        core: DesignCore,
        ui: UiSpec,
    ): Result<SoftwarePlan>
}


interface ProjectGraphCompiler {
    fun compile(
        core: DesignCore,
        ui: UiSpec,
        softwarePlan: SoftwarePlan,
    ): Result<ProjectGraph>
}
