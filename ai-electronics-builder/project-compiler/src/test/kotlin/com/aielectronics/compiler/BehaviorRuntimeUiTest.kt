package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.GoldenEngineeringCatalog
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class BehaviorRuntimeUiTest {

    private val capabilities = CapabilitySet(
        setOf(
            CapabilityId("measure_temperature"),
            CapabilityId("measure_humidity"),
            CapabilityId("actuate_fan"),
            CapabilityId("automation_rules"),
            CapabilityId("logging"),
            CapabilityId("manual_override"),
            CapabilityId("persistent_settings"),
            CapabilityId("generated_ui"),
            CapabilityId("self_test"),
            CapabilityId("failsafe"),
        )
    )

    @Test
    fun `behavior compiler creates runtime mutable hysteresis settings`() {
        val compiled = DefaultBehaviorCompiler()
            .compile(requirements(), capabilities)
            .getOrThrow()

        val settingIds = compiled.settings.map { it.id }.toSet()

        assertTrue(setOf("mode", "temp_on", "temp_off", "rh_on", "rh_off", "manual_fan")
            .all { it in settingIds })
        assertTrue(compiled.settings.all { it.mutableAtRuntime })
        assertEquals(60, compiled.logging?.intervalSeconds)
        assertEquals(7, compiled.logging?.retentionDays)
        assertTrue(compiled.graph.failsafe.any { it.id == "sensor_timeout" })
    }

    @Test
    fun `runtime manifest and phone UI are derived from the same design core`() {
        val core = goldenCore()

        val manifest = DefaultManifestCompiler(GoldenEngineeringCatalog)
            .compile(core)
            .getOrThrow()

        val ui = DefaultUiCompiler()
            .compile(core)
            .getOrThrow()

        assertEquals("xiao_esp32s3", manifest.boardId)
        assertEquals("1.1", manifest.version)
        assertEquals("0.2.0", manifest.minimumRuntimeVersion)
        assertEquals(
            CoreOperationMode.AUTONOMOUS_MCU,
            manifest.autonomy.coreOperationMode,
        )
        assertTrue(manifest.autonomy.localSafetyExecutionRequired)
        assertTrue(manifest.autonomy.persistRuntimeSettings)
        assertEquals("pin_xiao_d4_gpio5", manifest.buses.single().pins.getValue("SDA"))
        assertEquals("pin_xiao_d5_gpio6", manifest.buses.single().pins.getValue("SCL"))
        assertEquals("pin_xiao_d3_gpio4", manifest.gpio.single().pinId)
        assertTrue(manifest.settings.all { it.mutableAtRuntime })

        val dashboard = ui.pages.first { it.id == "dashboard" }
        assertTrue(dashboard.widgets.any { it.id == "temperature_value" })
        assertTrue(dashboard.widgets.any { it.id == "humidity_value" })
        assertTrue(dashboard.widgets.any { it.id == "manual_fan" })

        val settings = ui.pages.first { it.id == "settings" }
        assertTrue(settings.widgets.any { it.id == "temp_on" })
        assertTrue(settings.widgets.any { it.id == "rh_on" })

        assertNotNull(ui.pages.firstOrNull { it.id == "history" })
    }

    private fun goldenCore(): DesignCore {
        val req = requirements()
        val components = CatalogComponentResolver(GoldenEngineeringCatalog)
            .resolve(capabilities, req)
            .getOrThrow()
        val board = CatalogBoardSelector(GoldenEngineeringCatalog)
            .select(capabilities, components, req)
            .getOrThrow()
        val power = CatalogPowerPlanner(GoldenEngineeringCatalog)
            .plan(board, components, req)
            .getOrThrow()
        val pins = CatalogPinAllocator(GoldenEngineeringCatalog)
            .allocate(board, components)
            .getOrThrow()
        val graph = CatalogCircuitCompiler(GoldenEngineeringCatalog)
            .compile(board, components, power, pins)
            .getOrThrow()
        val report = CatalogElectricalValidator(GoldenEngineeringCatalog).validate(graph)
        val behavior = DefaultBehaviorCompiler().compile(req, capabilities).getOrThrow()

        return DefaultDesignCoreAssembler()
            .assemble(
                req,
                capabilities,
                board,
                components,
                graph,
                behavior,
                report,
                DefaultOfflineAutonomyCompiler()
                    .compile(req, capabilities, behavior)
                    .getOrThrow(),
            )
            .getOrThrow()
    }

    private fun requirements() = ResolvedRequirements(
        goal = "温度と湿度を記録し、暑くなったらファンを動かしてスマホから設定変更したい",
        slots = mapOf(
            "req_connectivity" to RequirementValue(
                "ble_local",
                RequirementSource.INFERRED_SAFE_DEFAULT,
                0.9,
            ),
            "temp_on" to RequirementValue("30.0", RequirementSource.USER, 1.0),
            "temp_off" to RequirementValue("28.0", RequirementSource.USER, 1.0),
            "rh_on" to RequirementValue("75.0", RequirementSource.USER, 1.0),
            "rh_off" to RequirementValue("70.0", RequirementSource.USER, 1.0),
        ),
    )
}
