package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog
import kotlin.math.abs

class CatalogPowerPlanner(
    private val catalog: EngineeringCatalog,
    private val currentMarginMultiplier: Double = 1.25,
) : PowerPlanner {

    override fun plan(
        board: BoardSelection,
        components: ResolvedComponents,
        requirements: ResolvedRequirements,
    ): Result<PowerPlan> = runCatching {
        val boardSpec = catalog.board(board.boardId)
            ?: error("Board spec not found: ${board.boardId}")

        val groups = linkedMapOf<Double, MutablePowerGroup>()

        components.components.forEach { instance ->
            val spec = catalog.component(instance.componentId)
                ?: error("Component spec not found: ${instance.componentId}")

            if (spec.supplyRole == SupplyRole.NONE) return@forEach

            val voltage = spec.preferredSupplyVoltageV
                ?: error("Preferred supply voltage missing: ${spec.componentId}")

            val group = groups.getOrPut(voltage) { MutablePowerGroup(voltage) }
            group.members += instance.instanceId
            spec.currentMaxMa?.let { group.knownCurrentMa += it }
                ?: group.unknownCurrentMembers.add(instance.instanceId)

            if (spec.requiresExternalPower || spec.supplyRole == SupplyRole.LOAD) {
                group.requiresExternalPower = true
            }
        }

        val domains = mutableListOf<PowerDomain>()
        val sources = linkedMapOf<String, PowerSource>()

        groups.values.forEach { group ->
            domains += PowerDomain(
                id = "rail_${formatVoltage(group.voltageV)}",
                nominalVoltageV = group.voltageV,
                maxRequiredCurrentMa = group.knownCurrentMa,
                memberIds = group.members,
                unknownCurrentMemberIds = group.unknownCurrentMembers,
            )

            if (!group.requiresExternalPower && group.voltageV in boardSpec.providedRailsV) {
                val id = "board_${boardSpec.boardId}_${formatVoltage(group.voltageV)}"
                sources[id] = PowerSource(
                    id = id,
                    nominalVoltageV = group.voltageV,
                    maxCurrentMa = null,
                    polarity = Polarity.NOT_APPLICABLE,
                    componentId = null,
                    verified = true,
                )
            } else {
                if (group.unknownCurrentMembers.isNotEmpty()) {
                    error(
                        "Cannot size external supply with unknown max current: " +
                            group.unknownCurrentMembers.joinToString()
                    )
                }

                val requiredWithMargin = group.knownCurrentMa * currentMarginMultiplier
                val supply = catalog.powerSupplies()
                    .asSequence()
                    .filter { it.designReady }
                    .filter { abs(it.outputVoltageV - group.voltageV) < 0.05 }
                    .filter { it.maxCurrentMa >= requiredWithMargin }
                    .sortedWith(
                        compareByDescending<PowerSupplySpec> { it.engineeringPriority }
                            .thenBy { it.supplyId }
                    )
                    .firstOrNull()
                    ?: error(
                        "No verified power supply for ${group.voltageV}V / " +
                            "${requiredWithMargin}mA required with margin"
                    )

                sources[supply.supplyId] = PowerSource(
                    id = supply.supplyId,
                    nominalVoltageV = supply.outputVoltageV,
                    maxCurrentMa = supply.maxCurrentMa,
                    polarity = supply.polarity,
                    componentId = supply.componentId,
                    verified = true,
                )
            }
        }

        PowerPlan(
            domains = domains,
            sources = sources.values.toList(),
        )
    }

    private data class MutablePowerGroup(
        val voltageV: Double,
        var knownCurrentMa: Double = 0.0,
        val members: MutableSet<String> = linkedSetOf(),
        val unknownCurrentMembers: MutableSet<String> = linkedSetOf(),
        var requiresExternalPower: Boolean = false,
    )

    private fun formatVoltage(value: Double): String =
        value.toString().replace(".", "v")
}
