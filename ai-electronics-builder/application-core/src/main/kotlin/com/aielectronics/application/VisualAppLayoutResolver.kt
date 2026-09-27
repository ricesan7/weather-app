package com.aielectronics.application

import com.aielectronics.core.model.AppHardwareIntegrationContract
import com.aielectronics.core.model.ProjectGraph
import com.aielectronics.core.model.ProjectGraphNodeKind

object VisualAppLayoutResolver {

    fun apply(
        graph: ProjectGraph,
        contract: AppHardwareIntegrationContract?,
        positions: Map<String, SavedGraphNodePosition>,
    ): AppHardwareIntegrationContract? {
        val source = contract ?: return null
        if (positions.isEmpty() || source.pages.isEmpty()) return source

        val applicationNodes = graph.nodes.filter {
            it.kind == ProjectGraphNodeKind.APP ||
                it.kind == ProjectGraphNodeKind.UI_PAGE ||
                it.kind == ProjectGraphNodeKind.UI_WIDGET
        }
        val defaultYByNodeId = applicationNodes
            .mapIndexed { index, node ->
                val y =
                    0.11 +
                        0.82 *
                        ((index + 1.0) / (applicationNodes.size + 1.0))
                node.id to y
            }
            .toMap()

        fun visualY(nodeId: String?): Double =
            nodeId?.let { id ->
                positions[id]?.y ?: defaultYByNodeId[id]
            } ?: Double.MAX_VALUE

        val pageNodeByPageId = graph.nodes
            .filter { it.kind == ProjectGraphNodeKind.UI_PAGE }
            .mapNotNull { node ->
                node.referenceId?.let { pageId -> pageId to node.id }
            }
            .toMap()

        val widgetNodeByKey = graph.nodes
            .filter { it.kind == ProjectGraphNodeKind.UI_WIDGET }
            .mapNotNull { node ->
                val pageId = node.metadata["pageId"] ?: return@mapNotNull null
                val widgetId = node.referenceId ?: return@mapNotNull null
                (pageId + "::" + widgetId) to node.id
            }
            .toMap()

        val orderedPages = source.pages
            .sortedWith(
                compareBy(
                    { page -> visualY(pageNodeByPageId[page.id]) },
                    { it.order },
                )
            )
            .mapIndexed { pageIndex, page ->
                val orderedWidgets = page.widgets
                    .sortedWith(
                        compareBy(
                            { widget ->
                                visualY(
                                    widgetNodeByKey[
                                        page.id + "::" + widget.id
                                    ]
                                )
                            },
                            { it.order },
                        )
                    )
                    .mapIndexed { widgetIndex, widget ->
                        widget.copy(order = widgetIndex)
                    }

                page.copy(
                    order = pageIndex,
                    widgets = orderedWidgets,
                )
            }

        return source.copy(pages = orderedPages)
    }
}
