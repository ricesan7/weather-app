package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog

class CatalogBoardSelector(
    private val catalog: EngineeringCatalog,
) : BoardSelector {

    override fun select(
        capabilities: CapabilitySet,
        components: ResolvedComponents,
        requirements: ResolvedRequirements,
    ): Result<BoardSelection> = runCatching {
        val requiredInterfaces = components.components
            .mapNotNull { catalog.component(it.componentId)?.primaryInterface }
            .toSet()

        val requiredTransport = when (requirements.slots["req_connectivity"]?.value) {
            "wifi_remote" -> TransportKind.WIFI
            "usb" -> TransportKind.USB
            else -> TransportKind.BLE
        }

        val preferredBoardId =
            requirements.slots["preferred_board_id"]?.value
                ?: requirements.slots["board_id"]?.value

        val candidates = catalog.boards()
            .asSequence()
            .filter { it.designReady }
            .filter { it.supportedInterfaces.containsAll(requiredInterfaces) }
            .filter { requiredTransport in it.transports }
            .toList()

        val board = if (preferredBoardId != null) {
            candidates.firstOrNull { it.boardId == preferredBoardId }
                ?: error("Preferred board is not compatible: $preferredBoardId")
        } else {
            candidates.sortedWith(
                compareByDescending<BoardSpec> { it.beginnerPriority }
                    .thenBy { it.boardId }
            ).firstOrNull()
                ?: error(
                    "No design-ready board supports interfaces=$requiredInterfaces " +
                        "transport=$requiredTransport"
                )
        }

        BoardSelection(
            boardId = board.boardId,
            transports = setOf(requiredTransport),
        )
    }
}
