package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog

class CatalogComponentResolver(
    private val catalog: EngineeringCatalog,
) : ComponentResolver {

    override fun resolve(
        capabilities: CapabilitySet,
        requirements: ResolvedRequirements,
    ): Result<ResolvedComponents> = runCatching {
        val uncovered = capabilities.values
            .filter(::looksLikeHardwareCapability)
            .toMutableSet()

        val selected = linkedMapOf<String, ComponentSpec>()

        while (uncovered.isNotEmpty()) {
            val candidate = catalog.components()
                .asSequence()
                .filter { it.designReady }
                .map { spec -> spec to spec.providesCapabilities.intersect(uncovered) }
                .filter { (_, covered) -> covered.isNotEmpty() }
                .sortedWith(
                    compareByDescending<Pair<ComponentSpec, Set<CapabilityId>>> { it.second.size }
                        .thenByDescending { it.first.engineeringPriority }
                        .thenBy { it.first.componentId }
                )
                .firstOrNull()
                ?: error(
                    "No design-ready component covers: " +
                        uncovered.joinToString { it.value }
                )

            addWithDependencies(candidate.first, selected)
            uncovered.removeAll(candidate.second)
        }

        ResolvedComponents(
            selected.values.mapIndexed { index, spec ->
                ComponentInstance(
                    instanceId = uniqueInstanceId(spec.defaultRole, index, selected.values.toList()),
                    componentId = spec.componentId,
                    role = spec.defaultRole,
                    properties = buildMap {
                        spec.driverId?.let { put("driver_id", it) }
                        spec.preferredSupplyVoltageV?.let { put("preferred_supply_v", it.toString()) }
                    },
                )
            }
        )
    }

    private fun addWithDependencies(
        spec: ComponentSpec,
        selected: LinkedHashMap<String, ComponentSpec>,
    ) {
        if (selected.putIfAbsent(spec.componentId, spec) != null) return

        spec.requiredSupportTags.sorted().forEach { tag ->
            val support = catalog.supportComponents(tag)
                .asSequence()
                .filter { it.designReady }
                .sortedWith(
                    compareByDescending<ComponentSpec> { it.engineeringPriority }
                        .thenBy { it.componentId }
                )
                .firstOrNull()
                ?: error("No design-ready support component for tag: $tag")

            addWithDependencies(support, selected)
        }
    }

    private fun uniqueInstanceId(
        role: String,
        index: Int,
        all: List<ComponentSpec>,
    ): String {
        val sameRoleCount = all.take(index + 1).count { it.defaultRole == role }
        return if (sameRoleCount <= 1) role else "${role}_$sameRoleCount"
    }

    private fun looksLikeHardwareCapability(capability: CapabilityId): Boolean =
        capability.value.startsWith("measure_") ||
            capability.value.startsWith("sense_") ||
            capability.value.startsWith("actuate_") ||
            capability.value.startsWith("display_")
}
