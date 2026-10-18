package com.aielectronics.regression

import com.aielectronics.application.SavedGraphNodePosition
import com.aielectronics.application.VisualAppLayoutResolver
import com.aielectronics.core.model.AppBridgePage
import com.aielectronics.core.model.AppBridgePresentation
import com.aielectronics.core.model.AppBridgeWidget
import com.aielectronics.core.model.AppHardwareIntegrationContract
import com.aielectronics.core.model.ProjectGraph
import com.aielectronics.core.model.ProjectGraphDomain
import com.aielectronics.core.model.ProjectGraphNode
import com.aielectronics.core.model.ProjectGraphNodeKind
import kotlin.test.Test
import kotlin.test.assertEquals

class VisualAppLayoutResolverTest {

    @Test
    fun `dragged page and widget positions reorder Base44 contract`() {
        val graph = ProjectGraph(
            nodes = listOf(
                ProjectGraphNode(
                    id = "application.base44",
                    label = "Base44 App",
                    domain = ProjectGraphDomain.APPLICATION,
                    kind = ProjectGraphNodeKind.APP,
                ),
                ProjectGraphNode(
                    id = "application.page.status",
                    label = "状態",
                    domain = ProjectGraphDomain.APPLICATION,
                    kind = ProjectGraphNodeKind.UI_PAGE,
                    referenceId = "status",
                ),
                ProjectGraphNode(
                    id = "application.widget.status.a",
                    label = "A",
                    domain = ProjectGraphDomain.APPLICATION,
                    kind = ProjectGraphNodeKind.UI_WIDGET,
                    referenceId = "a",
                    metadata = mapOf("pageId" to "status"),
                ),
                ProjectGraphNode(
                    id = "application.widget.status.b",
                    label = "B",
                    domain = ProjectGraphDomain.APPLICATION,
                    kind = ProjectGraphNodeKind.UI_WIDGET,
                    referenceId = "b",
                    metadata = mapOf("pageId" to "status"),
                ),
                ProjectGraphNode(
                    id = "application.page.settings",
                    label = "設定",
                    domain = ProjectGraphDomain.APPLICATION,
                    kind = ProjectGraphNodeKind.UI_PAGE,
                    referenceId = "settings",
                ),
            )
        )
        val contract = AppHardwareIntegrationContract(
            pages = listOf(
                AppBridgePage(
                    id = "status",
                    title = "状態",
                    order = 0,
                    widgets = listOf(
                        AppBridgeWidget(
                            id = "a",
                            binding = "telemetry.a",
                            displayName = "A",
                            presentation = AppBridgePresentation.VALUE,
                            order = 0,
                        ),
                        AppBridgeWidget(
                            id = "b",
                            binding = "telemetry.b",
                            displayName = "B",
                            presentation = AppBridgePresentation.VALUE,
                            order = 1,
                        ),
                    ),
                ),
                AppBridgePage(
                    id = "settings",
                    title = "設定",
                    order = 1,
                ),
            )
        )

        val resolved = VisualAppLayoutResolver.apply(
            graph = graph,
            contract = contract,
            positions = mapOf(
                "application.page.settings" to SavedGraphNodePosition(0.75, 0.20),
                "application.page.status" to SavedGraphNodePosition(0.75, 0.70),
                "application.widget.status.b" to SavedGraphNodePosition(0.75, 0.45),
                "application.widget.status.a" to SavedGraphNodePosition(0.75, 0.80),
            ),
        )!!

        assertEquals(
            listOf("settings", "status"),
            resolved.pages.map { it.id },
        )
        assertEquals(
            listOf("b", "a"),
            resolved.pages.first { it.id == "status" }.widgets.map { it.id },
        )
        assertEquals(listOf(0, 1), resolved.pages.map { it.order })
        assertEquals(
            listOf(0, 1),
            resolved.pages.first { it.id == "status" }.widgets.map { it.order },
        )
    }
}
