package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.GoldenEngineeringCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PinCircuitValidatorTest {

    private val catalog = GoldenEngineeringCatalog
    private val requirements = ResolvedRequirements(
        goal = "温度と湿度を監視して換気ファンを自動制御する",
        slots = mapOf(
            "req_connectivity" to RequirementValue(
                "ble_local",
                RequirementSource.INFERRED_SAFE_DEFAULT,
                0.9,
            )
        ),
    )

    @Test
    fun `XIAO golden design gets deterministic signal pins`() {
        val artifacts = buildArtifacts()

        val byRole = artifacts.pins.associateBy { it.logicalRole }

        assertEquals(
            "pin_xiao_d4_gpio5",
            byRole.getValue("environment_sensor.sda").pin.pinId,
        )
        assertEquals(
            "pin_xiao_d5_gpio6",
            byRole.getValue("environment_sensor.scl").pin.pinId,
        )
        assertEquals(
            "pin_xiao_d3_gpio4",
            byRole.getValue("load_driver.control").pin.pinId,
        )
    }

    @Test
    fun `golden circuit compiles to ten authoritative connections and passes validation`() {
        val artifacts = buildArtifacts()

        assertEquals(10, artifacts.graph.connections.size)

        val report = CatalogElectricalValidator(catalog).validate(artifacts.graph)
        assertEquals(ValidationState.PASS, report.state)
        assertTrue(report.issues.isEmpty())
    }

    @Test
    fun `removing common ground is blocked deterministically`() {
        val artifacts = buildArtifacts()

        val broken = artifacts.graph.copy(
            connections = artifacts.graph.connections.filterNot { connection ->
                connection.from.entityId.startsWith("supply_") &&
                    connection.from.pinId == "negative" &&
                    connection.to.entityId == "xiao_esp32s3"
            }
        )

        val report = CatalogElectricalValidator(catalog).validate(broken)

        assertEquals(ValidationState.BLOCKED, report.state)
        assertTrue(report.issues.any { it.code == "E_MISSING_COMMON_GND" })
    }

    private fun buildArtifacts(): Artifacts {
        val capabilities = CapabilitySet(
            setOf(
                CapabilityId("measure_temperature"),
                CapabilityId("measure_humidity"),
                CapabilityId("actuate_fan"),
            )
        )

        val components = CatalogComponentResolver(catalog)
            .resolve(capabilities, requirements)
            .getOrThrow()

        val board = CatalogBoardSelector(catalog)
            .select(capabilities, components, requirements)
            .getOrThrow()

        val power = CatalogPowerPlanner(catalog)
            .plan(board, components, requirements)
            .getOrThrow()

        val pins = CatalogPinAllocator(catalog)
            .allocate(board, components)
            .getOrThrow()

        val graph = CatalogCircuitCompiler(catalog)
            .compile(board, components, power, pins)
            .getOrThrow()

        return Artifacts(pins, graph)
    }

    private data class Artifacts(
        val pins: List<PinAssignment>,
        val graph: CircuitGraph,
    )
}
