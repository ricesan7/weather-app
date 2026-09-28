package com.aielectronics.parts

import com.aielectronics.core.model.CapabilityId
import com.aielectronics.core.model.ComponentKind
import com.aielectronics.core.model.ComponentSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ResearchedComponentCatalogTest {
    @Test
    fun `only ready records are exposed as compiler components`() {
        val catalog = MutableResearchedComponentCatalog()
        catalog.upsert(record("ready_sensor", ComponentUsabilityStatus.READY))
        catalog.upsert(record("partial_sensor", ComponentUsabilityStatus.NEEDS_INFO))
        catalog.upsert(record("unsupported_sensor", ComponentUsabilityStatus.UNSUPPORTED))

        assertEquals(listOf("ready_sensor"), catalog.components().map { it.componentId })
        assertEquals(
            ComponentUsabilityStatus.NEEDS_INFO,
            catalog.record("partial_sensor")?.evaluation?.status,
        )
        assertEquals(
            ComponentUsabilityStatus.UNSUPPORTED,
            catalog.record("unsupported_sensor")?.evaluation?.status,
        )
    }

    @Test
    fun `aliases resolve case insensitively without exposing rejected records`() {
        val catalog = MutableResearchedComponentCatalog()
        catalog.upsert(
            record(
                "ready_sensor",
                ComponentUsabilityStatus.READY,
                aliases = setOf("My Sensor", "SENSOR-123"),
            )
        )
        catalog.upsert(
            record(
                "rejected_sensor",
                ComponentUsabilityStatus.NEEDS_INFO,
                aliases = setOf("Pending Sensor"),
            )
        )

        assertEquals("ready_sensor", catalog.recordByAlias("my sensor")?.canonicalId)
        assertEquals("ready_sensor", catalog.recordByAlias("SENSOR-123")?.canonicalId)
        assertEquals("rejected_sensor", catalog.recordByAlias("pending sensor")?.canonicalId)
        assertNull(catalog.components().firstOrNull { it.componentId == "rejected_sensor" })
    }

    @Test
    fun `primary golden component wins canonical id collision`() {
        val primaryComponent = ComponentSpec(
            componentId = "same_id",
            displayName = "Golden",
            kind = ComponentKind.SENSOR,
            defaultRole = "sensor",
            providesCapabilities = setOf(CapabilityId("measure_temperature")),
            designReady = true,
        )
        val primary = object : EngineeringCatalog {
            override fun components() = listOf(primaryComponent)
            override fun boards() = emptyList<com.aielectronics.core.model.BoardSpec>()
            override fun powerSupplies() = emptyList<com.aielectronics.core.model.PowerSupplySpec>()
        }
        val overlay = MutableResearchedComponentCatalog()
        overlay.upsert(record("same_id", ComponentUsabilityStatus.READY))
        val composite = CompositeEngineeringCatalog(primary, overlay)

        assertSame(primaryComponent, composite.component("same_id"))
        assertEquals(1, composite.components().count { it.componentId == "same_id" })
    }

    @Test
    fun `composite preserves primary boards and power supplies`() {
        val composite = CompositeEngineeringCatalog(
            GoldenEngineeringCatalog,
            MutableResearchedComponentCatalog(),
        )

        assertEquals(GoldenEngineeringCatalog.boards(), composite.boards())
        assertEquals(GoldenEngineeringCatalog.powerSupplies(), composite.powerSupplies())
        assertTrue(composite.components().isNotEmpty())
    }

    private fun record(
        id: String,
        status: ComponentUsabilityStatus,
        aliases: Set<String> = emptySet(),
    ): ResearchedComponentRecord {
        val spec = ComponentSpec(
            componentId = id,
            displayName = id,
            kind = ComponentKind.SENSOR,
            defaultRole = "sensor",
            providesCapabilities = setOf(CapabilityId("measure_temperature")),
            designReady = status == ComponentUsabilityStatus.READY,
        )
        return ResearchedComponentRecord(
            canonicalId = id,
            requestedName = id,
            spec = spec,
            aliases = aliases,
            evaluation = ComponentUsabilityReport(status = status),
            researchedAtEpochMs = 1L,
        )
    }
}
