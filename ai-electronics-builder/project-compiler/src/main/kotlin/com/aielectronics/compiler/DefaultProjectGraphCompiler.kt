package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultProjectGraphCompiler : ProjectGraphCompiler {

    override fun compile(
        core: DesignCore,
        ui: UiSpec,
        softwarePlan: SoftwarePlan,
    ): Result<ProjectGraph> = runCatching {
        val nodes = linkedMapOf<String, ProjectGraphNode>()
        val edges = linkedMapOf<String, ProjectGraphEdge>()

        val boardNodeId = "hardware.board"
        nodes[boardNodeId] = ProjectGraphNode(
            id = boardNodeId,
            label = core.board.boardId,
            domain = ProjectGraphDomain.HARDWARE,
            kind = ProjectGraphNodeKind.BOARD,
            referenceId = core.board.boardId,
            metadata = mapOf(
                "transports" to core.board.transports.joinToString(",") { it.name },
            ),
        )

        val entityToNode = mutableMapOf<String, String>()
        entityToNode[core.board.boardId] = boardNodeId
        entityToNode["board"] = boardNodeId
        entityToNode["controller"] = boardNodeId
        entityToNode["mcu"] = boardNodeId

        core.components.forEach { component ->
            if (
                component.instanceId == "mcu" ||
                component.componentId == core.board.boardId
            ) {
                entityToNode[component.instanceId] = boardNodeId
                entityToNode[component.componentId] = boardNodeId
                return@forEach
            }

            val nodeId = "hardware.component." + component.instanceId
            nodes[nodeId] = ProjectGraphNode(
                id = nodeId,
                label = component.role.ifBlank { component.componentId },
                domain = ProjectGraphDomain.HARDWARE,
                kind = ProjectGraphNodeKind.COMPONENT,
                referenceId = component.instanceId,
                metadata = mapOf(
                    "componentId" to component.componentId,
                    "role" to component.role,
                ) + component.properties,
            )
            entityToNode[component.instanceId] = nodeId
            entityToNode[component.componentId] = nodeId
            entityToNode[component.role] = nodeId
        }

        core.power.sources
            .filter { it.componentId != null }
            .forEach { source ->
                val nodeId = "hardware.power." + source.id
                nodes[nodeId] = ProjectGraphNode(
                    id = nodeId,
                    label = source.componentId ?: source.id,
                    domain = ProjectGraphDomain.HARDWARE,
                    kind = ProjectGraphNodeKind.POWER_SOURCE,
                    referenceId = source.id,
                    metadata = buildMap {
                        put("voltageV", source.nominalVoltageV.toString())
                        source.maxCurrentMa?.let { current ->
                            put("maxCurrentMa", current.toString())
                        }
                    },
                )
            }

        core.connections.forEach { connection ->
            val from = entityToNode[connection.from.entityId] ?: boardNodeId
            val to = entityToNode[connection.to.entityId] ?: boardNodeId
            if (from != to) {
                val edgeId = "hardware.connection." + connection.id
                edges[edgeId] = ProjectGraphEdge(
                    id = edgeId,
                    fromNodeId = from,
                    toNodeId = to,
                    kind = ProjectGraphEdgeKind.HARDWARE_CONNECTION,
                    label = connection.netType.name,
                )
            }
        }

        core.settings.forEach { setting ->
            val nodeId = "behavior.setting." + setting.id
            nodes[nodeId] = ProjectGraphNode(
                id = nodeId,
                label = setting.id,
                domain = ProjectGraphDomain.BEHAVIOR,
                kind = ProjectGraphNodeKind.SETTING,
                referenceId = setting.id,
                metadata = mapOf(
                    "type" to setting.type.name,
                    "default" to setting.defaultValue,
                    "runtimeMutable" to setting.mutableAtRuntime.toString(),
                ),
            )
        }

        core.events.forEach { eventSpec ->
            val nodeId = "behavior.event." + eventSpec.id
            nodes[nodeId] = ProjectGraphNode(
                id = nodeId,
                label = eventSpec.id,
                domain = ProjectGraphDomain.BEHAVIOR,
                kind = ProjectGraphNodeKind.EVENT,
                referenceId = eventSpec.id,
                metadata = mapOf("severity" to eventSpec.severity.name),
            )
        }

        core.behavior.rules.forEach { rule ->
            val ruleNodeId = "behavior.rule." + rule.id
            nodes[ruleNodeId] = ProjectGraphNode(
                id = ruleNodeId,
                label = rule.id,
                domain = ProjectGraphDomain.BEHAVIOR,
                kind = ProjectGraphNodeKind.RULE,
                referenceId = rule.id,
                metadata = mapOf(
                    "priority" to rule.priority.toString(),
                    "condition" to expressionText(rule.condition),
                ),
            )

            val condition = expressionText(rule.condition)
            core.settings
                .filter { condition.contains(it.id) }
                .forEach { setting ->
                    val settingNodeId = "behavior.setting." + setting.id
                    val edgeId = "behavior.setting." + setting.id + "." + rule.id
                    edges[edgeId] = ProjectGraphEdge(
                        id = edgeId,
                        fromNodeId = settingNodeId,
                        toNodeId = ruleNodeId,
                        kind = ProjectGraphEdgeKind.USES_SETTING,
                    )
                }

            rule.actions.forEachIndexed { index, action ->
                when (action) {
                    is Action.SetOutput -> {
                        val target = entityToNode[action.outputId]
                            ?: core.components.firstOrNull {
                                it.role.contains(action.outputId, ignoreCase = true) ||
                                    it.instanceId.contains(action.outputId, ignoreCase = true)
                            }?.let { entityToNode[it.instanceId] }

                        if (target != null) {
                            val edgeId = "behavior.control." + rule.id + "." + index
                            edges[edgeId] = ProjectGraphEdge(
                                id = edgeId,
                                fromNodeId = ruleNodeId,
                                toNodeId = target,
                                kind = ProjectGraphEdgeKind.CONTROLS,
                                label = action.value,
                            )
                        }
                    }

                    is Action.RaiseEvent -> {
                        val eventNode = "behavior.event." + action.eventId
                        if (eventNode in nodes) {
                            val edgeId = "behavior.event." + rule.id + "." + index
                            edges[edgeId] = ProjectGraphEdge(
                                id = edgeId,
                                fromNodeId = ruleNodeId,
                                toNodeId = eventNode,
                                kind = ProjectGraphEdgeKind.EMITS_EVENT,
                            )
                        }
                    }

                    is Action.SetState -> Unit
                }
            }
        }

        val runtimeNodeId = "runtime.android_hardware_bridge"
        nodes[runtimeNodeId] = ProjectGraphNode(
            id = runtimeNodeId,
            label = "Android Hardware Bridge",
            domain = ProjectGraphDomain.RUNTIME,
            kind = ProjectGraphNodeKind.RUNTIME,
            metadata = mapOf(
                "transport" to softwarePlan.deviceBridge?.transport?.name.orEmpty(),
            ),
        )

        edges["runtime.deploy.board"] = ProjectGraphEdge(
            id = "runtime.deploy.board",
            fromNodeId = runtimeNodeId,
            toNodeId = boardNodeId,
            kind = ProjectGraphEdgeKind.DEPLOYS_TO,
        )

        if (softwarePlan.base44DesignRequired) {
            val appNodeId = "application.base44"
            nodes[appNodeId] = ProjectGraphNode(
                id = appNodeId,
                label = "Base44 App",
                domain = ProjectGraphDomain.APPLICATION,
                kind = ProjectGraphNodeKind.APP,
                metadata = mapOf(
                    "capabilities" to softwarePlan.base44Handoff
                        ?.requestedCapabilities
                        .orEmpty()
                        .joinToString(",") { it.name },
                ),
            )

            ui.pages.forEach { page ->
                val pageNodeId = "application.page." + page.id
                nodes[pageNodeId] = ProjectGraphNode(
                    id = pageNodeId,
                    label = page.title,
                    domain = ProjectGraphDomain.APPLICATION,
                    kind = ProjectGraphNodeKind.UI_PAGE,
                    referenceId = page.id,
                    metadata = mapOf("widgets" to page.widgets.size.toString()),
                )
                val edgeId = "application.renders." + page.id
                edges[edgeId] = ProjectGraphEdge(
                    id = edgeId,
                    fromNodeId = appNodeId,
                    toNodeId = pageNodeId,
                    kind = ProjectGraphEdgeKind.RENDERS,
                )
            }

            softwarePlan.base44Handoff
                ?.integration
                ?.channels
                .orEmpty()
                .forEachIndexed { index, channel ->
                    val kind = when (channel.direction) {
                        AppBridgeDirection.HARDWARE_TO_BASE44 ->
                            ProjectGraphEdgeKind.TELEMETRY_TO_APP
                        AppBridgeDirection.BASE44_TO_HARDWARE ->
                            ProjectGraphEdgeKind.COMMAND_TO_HARDWARE
                        AppBridgeDirection.HARDWARE_EVENT_TO_BASE44 ->
                            ProjectGraphEdgeKind.EVENT_TO_APP
                    }
                    val from = when (channel.direction) {
                        AppBridgeDirection.BASE44_TO_HARDWARE -> appNodeId
                        else -> runtimeNodeId
                    }
                    val to = when (channel.direction) {
                        AppBridgeDirection.BASE44_TO_HARDWARE -> runtimeNodeId
                        else -> appNodeId
                    }
                    val edgeId =
                        "application.bridge." + index + "." + channel.id
                    edges[edgeId] = ProjectGraphEdge(
                        id = edgeId,
                        fromNodeId = from,
                        toNodeId = to,
                        kind = kind,
                        label = channel.binding,
                    )
                }
        }

        ProjectGraph(
            nodes = nodes.values.toList(),
            edges = edges.values.toList(),
        )
    }

    private fun expressionText(expression: Expression): String = when (expression) {
        is Expression.Raw -> expression.expression
    }
}
