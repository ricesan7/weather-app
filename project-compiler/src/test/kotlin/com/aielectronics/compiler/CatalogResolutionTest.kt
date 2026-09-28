package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.GoldenEngineeringCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CatalogResolutionTest {

    private val catalog = GoldenEngineeringCatalog

    @Test
    fun `temperature humidity and fan resolve to sensor fan and verified driver`() {
        val resolved = CatalogComponentResolver(catalog).resolve(
            CapabilitySet(
                setOf(
                    CapabilityId("measure_temperature"),
                    CapabilityId("measure_humidity"),
                    CapabilityId("actuate_fan"),
                    CapabilityId("logging"),
                )
            ),
            requirements(),
        ).getOrThrow()

        assertEquals(
            setOf("ae_sht31", "fan_ydm2510c05", "tbd62003apg"),
            resolved.components.map { it.componentId }.toSet(),
        )
    }


    @Test
    fun `uncovered hardware capability is reported with a typed exception`() {
        val failure = CatalogComponentResolver(catalog).resolve(
            CapabilitySet(setOf(CapabilityId("measure_light"))),
            requirements(),
        ).exceptionOrNull()

        val unresolved = kotlin.test.assertIs<UnresolvedHardwareCapabilitiesException>(failure)
        assertEquals(setOf(CapabilityId("measure_light")), unresolved.capabilities)
    }

    @Test
    fun `board selector chooses XIAO for beginner BLE design`() {
        val components = resolvedComponents()
        val board = CatalogBoardSelector(catalog)
            .select(CapabilitySet(emptySet()), components, requirements())
            .getOrThrow()

        assertEquals("xiao_esp32s3", board.boardId)
        assertEquals(setOf(TransportKind.BLE), board.transports)
    }

    @Test
    fun `power planner separates logic and external 5V fan supply`() {
        val components = resolvedComponents()
        val board = BoardSelection("xiao_esp32s3", setOf(TransportKind.BLE))

        val plan = CatalogPowerPlanner(catalog)
            .plan(board, components, requirements())
            .getOrThrow()

        val load5v = plan.domains.first { it.nominalVoltageV == 5.0 }
        assertEquals(140.0, load5v.maxRequiredCurrentMa)
        assertTrue(load5v.unknownCurrentMemberIds.isEmpty())

        val logic3v3 = plan.domains.first { it.nominalVoltageV == 3.3 }
        assertTrue("environment_sensor" in logic3v3.unknownCurrentMemberIds)

        val external = plan.sources.firstOrNull { it.componentId == "psu_ad_t50p200_5v2a" }
        assertNotNull(external)
        assertEquals(2000.0, external.maxCurrentMa)
        assertEquals(Polarity.CENTER_POSITIVE, external.polarity)
    }

    private fun resolvedComponents(): ResolvedComponents =
        CatalogComponentResolver(catalog).resolve(
            CapabilitySet(
                setOf(
                    CapabilityId("measure_temperature"),
                    CapabilityId("measure_humidity"),
                    CapabilityId("actuate_fan"),
                )
            ),
            requirements(),
        ).getOrThrow()

    private fun requirements() = ResolvedRequirements(
        goal = "温湿度を監視して換気ファンを動かす",
        slots = mapOf(
            "req_connectivity" to RequirementValue("ble_local", RequirementSource.INFERRED_SAFE_DEFAULT, 0.9)
        ),
    )
}
