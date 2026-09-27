package com.aielectronics.core.model

enum class ProjectGraphDomain {
    HARDWARE,
    BEHAVIOR,
    APPLICATION,
    RUNTIME,
}

enum class ProjectGraphNodeKind {
    BOARD,
    COMPONENT,
    POWER_SOURCE,
    RULE,
    SETTING,
    EVENT,
    APP,
    UI_PAGE,
    UI_WIDGET,
    RUNTIME,
}

data class ProjectGraphLane(
    val domain: ProjectGraphDomain,
    val title: String,
    val order: Int,
)

data class ProjectGraphNode(
    val id: String,
    val label: String,
    val domain: ProjectGraphDomain,
    val kind: ProjectGraphNodeKind,
    val referenceId: String? = null,
    val metadata: Map<String, String> = emptyMap(),
)

enum class ProjectGraphEdgeKind {
    HARDWARE_CONNECTION,
    SUPPLIES,
    CONTROLS,
    USES_SETTING,
    EMITS_EVENT,
    RENDERS,
    TELEMETRY_TO_APP,
    COMMAND_TO_HARDWARE,
    EVENT_TO_APP,
    DEPLOYS_TO,
}

data class ProjectGraphEdge(
    val id: String,
    val fromNodeId: String,
    val toNodeId: String,
    val kind: ProjectGraphEdgeKind,
    val label: String? = null,
)

data class ProjectGraph(
    val schemaVersion: String = "1.0",
    val lanes: List<ProjectGraphLane> = defaultProjectGraphLanes(),
    val nodes: List<ProjectGraphNode> = emptyList(),
    val edges: List<ProjectGraphEdge> = emptyList(),
) {
    companion object {
        val EMPTY = ProjectGraph()
    }
}

fun defaultProjectGraphLanes(): List<ProjectGraphLane> = listOf(
    ProjectGraphLane(ProjectGraphDomain.HARDWARE, "ハードウェア", 0),
    ProjectGraphLane(ProjectGraphDomain.BEHAVIOR, "動作", 1),
    ProjectGraphLane(ProjectGraphDomain.APPLICATION, "アプリ", 2),
    ProjectGraphLane(ProjectGraphDomain.RUNTIME, "実機Runtime", 3),
)
