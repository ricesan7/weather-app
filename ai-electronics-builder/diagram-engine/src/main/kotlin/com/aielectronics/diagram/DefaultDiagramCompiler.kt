package com.aielectronics.diagram

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog

class DefaultDiagramCompiler(
    private val engineeringCatalog: EngineeringCatalog,
    private val assetCatalog: DiagramAssetCatalog = GoldenDiagramAssetCatalog,
) {
    fun compile(graph: CircuitGraph): Result<DiagramSpec> = runCatching {
        val placements = createPlacements(graph)
        val placementByEntity = placements.associateBy { it.entityId }

        val wires = graph.connections.mapIndexed { index, connection ->
            val fromPoint = pinPoint(connection.from, placementByEntity)
            val toPoint = pinPoint(connection.to, placementByEntity)
            DiagramWire(
                connectionId = connection.id,
                from = connection.from,
                to = connection.to,
                points = route(fromPoint, toPoint, index),
                netType = connection.netType,
                wireSemantic = connection.wireSemantic,
                label = connectionLabel(connection, placementByEntity),
            )
        }

        require(wires.map { it.connectionId }.toSet() ==
            graph.connections.map { it.id }.toSet()) {
            "CircuitGraph/Diagram wire mismatch"
        }

        val steps = graph.connections.mapIndexed { index, connection ->
            val viewId = "step_" + (index + 1).toString().padStart(2, '0')
            GuidedBuildStep(
                order = index + 1,
                connectionId = connection.id,
                title = "配線 " + (index + 1) + " / " + graph.connections.size,
                instruction = instruction(connection, placementByEntity),
                diagramViewId = viewId,
            )
        }

        val powerIds = graph.connections
            .filter { it.netType == NetType.POWER || it.netType == NetType.GROUND }
            .map { it.id }

        val views = buildList {
            add(
                DiagramView(
                    id = "system_overview",
                    kind = DiagramViewKind.SYSTEM_OVERVIEW,
                    title = "全体構成",
                )
            )
            add(
                DiagramView(
                    id = "physical_wiring",
                    kind = DiagramViewKind.PHYSICAL_WIRING,
                    title = "正確な配線",
                )
            )
            add(
                DiagramView(
                    id = "power_check",
                    kind = DiagramViewKind.POWER_CHECK,
                    highlightedConnectionIds = powerIds,
                    title = "電源・GND確認",
                )
            )
            steps.forEach { step ->
                add(
                    DiagramView(
                        id = step.diagramViewId,
                        kind = DiagramViewKind.SOLDER_STEP,
                        highlightedConnectionIds = listOf(step.connectionId),
                        title = step.title,
                    )
                )
            }
        }

        DiagramSpec(
            views = views,
            placements = placements,
            wires = wires,
            buildPlan = GuidedBuildPlan(steps),
        )
    }

    private fun createPlacements(graph: CircuitGraph): List<DiagramPlacement> {
        val placements = mutableListOf<DiagramPlacement>()

        val boardSpec = engineeringCatalog.board(graph.board.boardId)
            ?: error("Board spec missing: " + graph.board.boardId)
        val boardAsset = assetCatalog.asset(graph.board.boardId)
            ?: error("Board visual asset missing: " + graph.board.boardId)

        placements += placement(
            entityId = graph.board.boardId,
            label = boardSpec.displayName,
            asset = boardAsset,
            origin = VisualPoint(70.0, 190.0),
        )

        var sensorIndex = 0
        var driverIndex = 0
        var actuatorIndex = 0
        var genericIndex = 0

        graph.components.forEach { instance ->
            val spec = engineeringCatalog.component(instance.componentId)
                ?: error("Component spec missing: " + instance.componentId)
            val asset = assetCatalog.asset(instance.componentId)
                ?: error("Component visual asset missing: " + instance.componentId)

            val origin = when (spec.kind) {
                ComponentKind.SENSOR -> {
                    val p = VisualPoint(390.0, 45.0 + sensorIndex * 160.0)
                    sensorIndex += 1
                    p
                }
                ComponentKind.DRIVER -> {
                    val p = VisualPoint(390.0, 255.0 + driverIndex * 210.0)
                    driverIndex += 1
                    p
                }
                ComponentKind.ACTUATOR -> {
                    val p = VisualPoint(690.0, 260.0 + actuatorIndex * 190.0)
                    actuatorIndex += 1
                    p
                }
                else -> {
                    val p = VisualPoint(650.0, 60.0 + genericIndex * 150.0)
                    genericIndex += 1
                    p
                }
            }

            placements += placement(
                entityId = instance.instanceId,
                label = spec.displayName,
                asset = asset,
                origin = origin,
            )
        }

        graph.power.sources
            .filter { it.componentId != null }
            .forEachIndexed { index, source ->
                val supply = engineeringCatalog.powerSupplies()
                    .firstOrNull { it.componentId == source.componentId }
                    ?: error("Power supply spec missing: " + source.componentId)
                val asset = assetCatalog.asset(source.componentId!!)
                    ?: error("Power visual asset missing: " + source.componentId)

                placements += placement(
                    entityId = source.id,
                    label = supply.displayName,
                    asset = asset,
                    origin = VisualPoint(70.0 + index * 230.0, 520.0),
                )
            }

        return placements
    }

    private fun placement(
        entityId: String,
        label: String,
        asset: DiagramAsset,
        origin: VisualPoint,
    ): DiagramPlacement =
        DiagramPlacement(
            entityId = entityId,
            assetId = asset.assetId,
            label = label,
            kind = asset.kind,
            origin = origin,
            size = asset.size,
            pins = asset.pinAnchors.map { (pinId, anchor) ->
                DiagramPin(
                    pinId = pinId,
                    label = anchor.label,
                    point = VisualPoint(
                        x = origin.x + anchor.point.x,
                        y = origin.y + anchor.point.y,
                    ),
                )
            },
        )

    private fun pinPoint(
        ref: PinRef,
        placements: Map<String, DiagramPlacement>,
    ): VisualPoint {
        val placement = placements[ref.entityId]
            ?: error("Diagram entity missing: " + ref.entityId)
        return placement.pins.firstOrNull { it.pinId == ref.pinId }?.point
            ?: error("Diagram pin anchor missing: " + ref.entityId + "/" + ref.pinId)
    }

    private fun route(
        from: VisualPoint,
        to: VisualPoint,
        index: Int,
    ): List<VisualPoint> {
        val laneOffset = ((index % 7) - 3) * 12.0
        val middleX = (from.x + to.x) / 2.0 + laneOffset
        return listOf(
            from,
            VisualPoint(middleX, from.y),
            VisualPoint(middleX, to.y),
            to,
        )
    }

    private fun connectionLabel(
        connection: Connection,
        placements: Map<String, DiagramPlacement>,
    ): String =
        endpointLabel(connection.from, placements) + " → " +
            endpointLabel(connection.to, placements)

    private fun instruction(
        connection: Connection,
        placements: Map<String, DiagramPlacement>,
    ): String =
        endpointLabel(connection.from, placements) + " と " +
            endpointLabel(connection.to, placements) +
            " を接続します。"

    private fun endpointLabel(
        ref: PinRef,
        placements: Map<String, DiagramPlacement>,
    ): String {
        val placement = placements.getValue(ref.entityId)
        val pin = placement.pins.first { it.pinId == ref.pinId }
        return placement.label + " の " + pin.label
    }
}
