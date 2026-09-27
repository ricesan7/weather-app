package com.aielectronics.application

import com.aielectronics.compiler.CatalogBoardSelector
import com.aielectronics.compiler.CatalogCircuitCompiler
import com.aielectronics.compiler.CatalogComponentResolver
import com.aielectronics.compiler.CatalogElectricalValidator
import com.aielectronics.compiler.CatalogPinAllocator
import com.aielectronics.compiler.CatalogPowerPlanner
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.DefaultBehaviorCompiler
import com.aielectronics.compiler.DefaultCapabilityMapper
import com.aielectronics.compiler.DefaultDesignCoreAssembler
import com.aielectronics.compiler.DefaultDiagnosticCompiler
import com.aielectronics.compiler.DefaultManifestCompiler
import com.aielectronics.compiler.DefaultProjectCompiler
import com.aielectronics.compiler.DefaultRequirementResolver
import com.aielectronics.compiler.DefaultSoftwareArchitectureCompiler
import com.aielectronics.compiler.DefaultUiCompiler
import com.aielectronics.compiler.DiagramCompiler
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.CircuitGraph
import com.aielectronics.core.model.DiagramSpec
import com.aielectronics.core.model.IntentDraft
import com.aielectronics.core.model.ResolvedRequirements
import com.aielectronics.diagram.DefaultDiagramCompiler
import com.aielectronics.parts.EngineeringCatalog
import com.aielectronics.parts.GoldenEngineeringCatalog

class ApplicationProjectEngine(
    private val catalog: EngineeringCatalog = GoldenEngineeringCatalog,
) {
    private val requirementResolver = DefaultRequirementResolver()
    private val deterministicDiagramCompiler = DefaultDiagramCompiler(catalog)

    private val compiler = DefaultProjectCompiler(
        capabilityMapper = DefaultCapabilityMapper(),
        componentResolver = CatalogComponentResolver(catalog),
        boardSelector = CatalogBoardSelector(catalog),
        powerPlanner = CatalogPowerPlanner(catalog),
        pinAllocator = CatalogPinAllocator(catalog),
        circuitCompiler = CatalogCircuitCompiler(catalog),
        electricalValidator = CatalogElectricalValidator(catalog),
        behaviorCompiler = DefaultBehaviorCompiler(),
        coreAssembler = DefaultDesignCoreAssembler(),
        diagramCompiler = object : DiagramCompiler {
            override fun compile(graph: CircuitGraph): Result<DiagramSpec> =
                deterministicDiagramCompiler.compile(graph)
        },
        manifestCompiler = DefaultManifestCompiler(catalog),
        uiCompiler = DefaultUiCompiler(),
        diagnosticCompiler = DefaultDiagnosticCompiler(),
        softwareArchitectureCompiler = DefaultSoftwareArchitectureCompiler(),
    )

    fun resolve(intent: IntentDraft): RequirementResolution =
        requirementResolver.resolve(intent)

    fun compile(requirements: ResolvedRequirements): CompileResult =
        compiler.compile(requirements)
}
