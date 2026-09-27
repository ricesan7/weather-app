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
        val explicitlyRequested = requirements.requestedComponents
            .filterNot { it.categoryHint == "board" }

        val missingRequests = mutableListOf<ComponentResearchRequest>()
        val selectedSpecs = mutableListOf<ComponentSpec>()
        val supportComponents = linkedMapOf<String, ComponentSpec>()

        explicitlyRequested.forEachIndexed { index, requested ->
            val resolved = findRequestedComponent(requested.rawName)
            if (resolved == null) {
                missingRequests += ComponentResearchRequest(
                    requestId =
                        "research_" +
                            normalize(requested.rawName)
                                .ifBlank { index.toString() },
                    requested = requested,
                    requiredCapabilities =
                        inferCapabilities(requested),
                    projectGoal = requirements.goal,
                )
                return@forEachIndexed
            }

            repeat(requested.quantity.coerceAtLeast(1)) {
                selectedSpecs += resolved
            }
            addSupportDependencies(resolved, supportComponents)
        }

        if (missingRequests.isNotEmpty()) {
            throw ComponentResearchRequiredException(missingRequests)
        }

        val coveredByExplicit = selectedSpecs
            .flatMap { it.providesCapabilities }
            .toSet()

        val uncovered = capabilities.values
            .filter(::looksLikeHardwareCapability)
            .filterNot { it in coveredByExplicit }
            .toMutableSet()

        val automaticallySelected = linkedMapOf<String, ComponentSpec>()

        while (uncovered.isNotEmpty()) {
            val candidate = catalog.components()
                .asSequence()
                .filter { it.designReady }
                .map {
                    spec ->
                    spec to
                        spec.providesCapabilities.intersect(uncovered)
                }
                .filter { (_, covered) -> covered.isNotEmpty() }
                .sortedWith(
                    compareByDescending<
                        Pair<ComponentSpec, Set<CapabilityId>>
                    > { it.second.size }
                        .thenByDescending {
                            it.first.engineeringPriority
                        }
                        .thenBy { it.first.componentId }
                )
                .firstOrNull()
                ?: error(
                    "No design-ready component covers: " +
                        uncovered.joinToString { it.value }
                )

            if (
                selectedSpecs.none {
                    it.componentId == candidate.first.componentId
                }
            ) {
                automaticallySelected.putIfAbsent(
                    candidate.first.componentId,
                    candidate.first,
                )
            }
            addSupportDependencies(
                candidate.first,
                supportComponents,
            )
            uncovered.removeAll(candidate.second)
        }

        val allSpecs = buildList {
            addAll(selectedSpecs)
            addAll(automaticallySelected.values)
            supportComponents.values.forEach { support ->
                if (
                    none {
                        it.componentId == support.componentId
                    }
                ) {
                    add(support)
                }
            }
        }

        ResolvedComponents(
            allSpecs.mapIndexed { index, spec ->
                ComponentInstance(
                    instanceId =
                        uniqueInstanceId(
                            spec.defaultRole,
                            index,
                            allSpecs,
                        ),
                    componentId = spec.componentId,
                    role = spec.defaultRole,
                    properties = buildMap {
                        spec.driverId?.let {
                            put("driver_id", it)
                        }
                        spec.preferredSupplyVoltageV?.let {
                            put(
                                "preferred_supply_v",
                                it.toString(),
                            )
                        }
                        put(
                            "verification_status",
                            spec.verificationStatus.name,
                        )
                    },
                )
            }
        )
    }

    private fun findRequestedComponent(
        requestedName: String,
    ): ComponentSpec? {
        val requested = normalize(requestedName)

        return catalog.components()
            .asSequence()
            .filter { it.designReady }
            .map { spec ->
                spec to buildSet {
                    add(normalize(spec.componentId))
                    add(normalize(spec.displayName))
                    spec.aliases.forEach {
                        add(normalize(it))
                    }
                }
            }
            .filter { (_, names) ->
                names.any { name ->
                    name == requested ||
                        (
                            requested.length >= 4 &&
                                (
                                    name.contains(requested) ||
                                        requested.contains(name)
                                )
                        )
                }
            }
            .map { it.first }
            .sortedWith(
                compareByDescending<ComponentSpec> {
                    it.engineeringPriority
                }.thenBy { it.componentId }
            )
            .firstOrNull()
    }

    private fun addSupportDependencies(
        spec: ComponentSpec,
        selected: LinkedHashMap<String, ComponentSpec>,
    ) {
        spec.requiredSupportTags.sorted().forEach { tag ->
            val support = catalog.supportComponents(tag)
                .asSequence()
                .filter { it.designReady }
                .sortedWith(
                    compareByDescending<ComponentSpec> {
                        it.engineeringPriority
                    }.thenBy { it.componentId }
                )
                .firstOrNull()
                ?: error(
                    "No design-ready support component for tag: $tag"
                )

            if (
                selected.putIfAbsent(
                    support.componentId,
                    support,
                ) == null
            ) {
                addSupportDependencies(
                    support,
                    selected,
                )
            }
        }
    }

    private fun inferCapabilities(
        requested: RequestedComponent,
    ): Set<CapabilityId> {
        val text =
            (
                requested.rawName +
                    " " +
                    requested.categoryHint.orEmpty()
            ).lowercase()
        return buildSet {
            if (
                text.contains("温度") ||
                text.contains("dht") ||
                text.contains("temperature")
            ) {
                add(CapabilityId("measure_temperature"))
            }
            if (
                text.contains("湿度") ||
                text.contains("dht") ||
                text.contains("humidity")
            ) {
                add(CapabilityId("measure_humidity"))
            }
            if (
                text.contains("oled") ||
                text.contains("display") ||
                text.contains("ディスプレイ")
            ) {
                add(CapabilityId("display_visual"))
            }
            if (
                text.contains("button") ||
                text.contains("switch") ||
                text.contains("ボタン") ||
                text.contains("スイッチ")
            ) {
                add(CapabilityId("sense_button"))
            }
            if (
                text.contains("電源") ||
                text.contains("power")
            ) {
                add(CapabilityId("manage_power_input"))
            }
        }
    }

    private fun uniqueInstanceId(
        role: String,
        index: Int,
        all: List<ComponentSpec>,
    ): String {
        val sameRoleCount =
            all.take(index + 1)
                .count {
                    it.defaultRole == role
                }
        return if (sameRoleCount <= 1) {
            role
        } else {
            "${role}_${sameRoleCount}"
        }
    }

    private fun looksLikeHardwareCapability(
        capability: CapabilityId,
    ): Boolean =
        capability.value.startsWith("measure_") ||
            capability.value.startsWith("sense_") ||
            capability.value.startsWith("actuate_") ||
            capability.value.startsWith("display_") ||
            capability.value.startsWith("manage_power_")

    private fun normalize(value: String): String =
        value.lowercase().replace(
            Regex("""[^a-z0-9ぁ-んァ-ヶ一-龠]+"""),
            "",
        )
}
