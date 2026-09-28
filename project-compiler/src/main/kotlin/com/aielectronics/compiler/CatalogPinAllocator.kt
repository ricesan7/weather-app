package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog

class CatalogPinAllocator(
    private val catalog: EngineeringCatalog,
) : PinAllocator {

    override fun allocate(
        board: BoardSelection,
        components: ResolvedComponents,
    ): Result<List<PinAssignment>> = runCatching {
        val boardSpec = catalog.board(board.boardId)
            ?: error("Board spec not found: ${board.boardId}")

        val usedPins = mutableSetOf<String>()
        val sharedPins = mutableMapOf<BoardPinCapability, BoardPinSpec>()
        val assignments = mutableListOf<PinAssignment>()

        components.components
            .sortedBy { it.instanceId }
            .forEach { instance ->
                val spec = catalog.component(instance.componentId)
                    ?: error("Component spec not found: ${instance.componentId}")

                spec.signalRequirements.forEach { requirement ->
                    val boardPin = if (requirement.shareable) {
                        sharedPins[requirement.boardCapability]
                            ?: selectPin(boardSpec, requirement, usedPins).also {
                                sharedPins[requirement.boardCapability] = it
                                usedPins += it.pinId
                            }
                    } else {
                        selectPin(boardSpec, requirement, usedPins).also {
                            usedPins += it.pinId
                        }
                    }

                    val targetPin = spec.pins.firstOrNull {
                        it.role == requirement.componentPinRole
                    } ?: error(
                        "Component pin missing for ${spec.componentId}: " +
                            "${requirement.componentPinRole}"
                    )

                    assignments += PinAssignment(
                        logicalRole = "${instance.instanceId}.${requirement.id}",
                        pin = PinRef(board.boardId, boardPin.pinId),
                        target = PinRef(instance.instanceId, targetPin.pinId),
                        netType = requirement.netType,
                        wireSemantic = requirement.wireSemantic,
                    )
                }
            }

        assignments
    }

    private fun selectPin(
        board: BoardSpec,
        requirement: SignalRequirement,
        usedPins: Set<String>,
    ): BoardPinSpec =
        board.pins
            .asSequence()
            .filter { requirement.boardCapability in it.capabilities }
            .filter { it.pinId !in usedPins }
            .sortedWith(
                compareByDescending<BoardPinSpec> { it.allocationPriority }
                    .thenBy { it.pinId }
            )
            .firstOrNull()
            ?: error(
                "No free board pin for ${requirement.boardCapability} " +
                    "on ${board.boardId}"
            )
}
