package com.aielectronics.diagram

import com.aielectronics.compiler.*
import com.aielectronics.core.model.*
import com.aielectronics.parts.GoldenEngineeringCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DiagramEngineTest {

    @Test
    fun `Golden circuit produces one visual wire and one build step per connection`() {
        val graph = goldenGraph()
        val spec = DefaultDiagramCompiler(GoldenEngineeringCatalog)
            .compile(graph)
            .getOrThrow()

        assertEquals(10, graph.connections.size)
        assertEquals(10, spec.wires.size)
        assertEquals(
            graph.connections.map { it.id }.toSet(),
            spec.wires.map { it.connectionId }.toSet(),
        )
        assertEquals(10, spec.buildPlan?.steps?.size)
        assertTrue(spec.placements.any { it.entityId == "xiao_esp32s3" })
        assertTrue(spec.placements.any { it.entityId == "environment_sensor" })
        assertTrue(spec.placements.any { it.entityId == "fan" })
    }

    @Test
    fun `every solder step highlights exactly its authoritative connection`() {
        val spec = DefaultDiagramCompiler(GoldenEngineeringCatalog)
            .compile(goldenGraph())
            .getOrThrow()

        spec.buildPlan!!.steps.forEach { step ->
            val view = spec.views.first { it.id == step.diagramViewId }
            assertEquals(listOf(step.connectionId), view.highlightedConnectionIds)
        }
    }

    @Test
    fun `SVG renderer preserves connection IDs for interactive phone highlighting`() {
        val spec = DefaultDiagramCompiler(GoldenEngineeringCatalog)
            .compile(goldenGraph())
            .getOrThrow()

        val svg = SvgDiagramRenderer().render(spec, "physical_wiring")

        assertTrue(svg.startsWith("<svg"))
        spec.wires.forEach { wire ->
            assertTrue(svg.contains("data-connection-id=\"" + wire.connectionId + "\""))
        }
        assertTrue(svg.contains("XIAO ESP32S3"))
        assertTrue(svg.contains("AE-SHT31"))
        assertTrue(svg.contains("YDM2510C05"))
    }

    private fun goldenGraph(): CircuitGraph {
        val capabilities = CapabilitySet(
            setOf(
                CapabilityId("measure_temperature"),
                CapabilityId("measure_humidity"),
                CapabilityId("actuate_fan"),
            )
        )
        val requirements = ResolvedRequirements(
            goal = "温湿度を監視して換気ファンを動かす",
            slots = mapOf(
                "req_connectivity" to RequirementValue(
                    "ble_local",
                    RequirementSource.INFERRED_SAFE_DEFAULT,
                    0.9,
                )
            ),
        )

        val components = CatalogComponentResolver(GoldenEngineeringCatalog)
            .resolve(capabilities, requirements)
            .getOrThrow()
        val board = CatalogBoardSelector(GoldenEngineeringCatalog)
            .select(capabilities, components, requirements)
            .getOrThrow()
        val power = CatalogPowerPlanner(GoldenEngineeringCatalog)
            .plan(board, components, requirements)
            .getOrThrow()
        val pins = CatalogPinAllocator(GoldenEngineeringCatalog)
            .allocate(board, components)
            .getOrThrow()

        return CatalogCircuitCompiler(GoldenEngineeringCatalog)
            .compile(board, components, power, pins)
            .getOrThrow()
    }
}
