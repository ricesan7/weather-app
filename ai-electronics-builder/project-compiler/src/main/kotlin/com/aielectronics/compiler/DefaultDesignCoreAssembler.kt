package com.aielectronics.compiler

import com.aielectronics.core.model.*
import kotlin.math.absoluteValue

class DefaultDesignCoreAssembler : DesignCoreAssembler {

    override fun assemble(
        requirements: ResolvedRequirements,
        capabilities: CapabilitySet,
        board: BoardSelection,
        components: ResolvedComponents,
        circuitGraph: CircuitGraph,
        behavior: BehaviorGraph,
        validation: ValidationReport,
    ): Result<DesignCore> = runCatching {
        val projectId = requirements.slots["project_id"]?.value
            ?: "project_${requirements.goal.hashCode().absoluteValue.toString(16)}"

        val projectName = requirements.slots["project_name"]?.value
            ?: requirements.goal.take(40)

        val assemblyMode = when (requirements.slots["req_installation"]?.value) {
            "breadboard_prototype" -> AssemblyMode.BREADBOARD_PROTOTYPE
            "advanced_freeform" -> AssemblyMode.ADVANCED_FREEFORM
            else -> AssemblyMode.GUIDED_SOLDER
        }

        DesignCore(
            schemaVersion = "1.0",
            project = ProjectInfo(
                id = projectId,
                name = projectName,
                goal = requirements.goal,
            ),
            assumptions = requirements.assumptions,
            unresolved = requirements.unresolved.map {
                UnresolvedRequirement(
                    id = it.slotId,
                    reason = it.reason,
                    blocking = it.blocking,
                )
            },
            capabilities = capabilities.values,
            board = board,
            power = circuitGraph.power,
            components = components.components,
            connections = circuitGraph.connections,
            behavior = behavior,
            settings = emptyList(),
            logging = null,
            events = emptyList(),
            assembly = AssemblySpec(
                mode = assemblyMode,
                stepIds = emptyList(),
            ),
            deployment = DeploymentSpec(
                firmwareProfileId = "fw_beginner_runtime",
                manifestVersion = "1.0",
            ),
            safety = SafetySummary(
                state = validation.state,
                issues = validation.issues,
            ),
        )
    }
}
