package com.aielectronics.builder

import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.*
import com.aielectronics.parts.CompositeEngineeringCatalog
import com.aielectronics.parts.GoldenEngineeringCatalog
import com.aielectronics.parts.ResearchedComponentSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AppProjectEngineTest {

    private val interpreter = BeginnerIntentInterpreter()
    private val engine = ApplicationProjectEngine()

    @Test
    fun `golden beginner request compiles end to end from natural language`() {
        val intent = interpreter.interpret(
            "温度が30℃以上になったらファンを自動で回したい。履歴もスマホで見たい。"
        )

        val resolution = assertIs<RequirementResolution.Ready>(
            engine.resolve(intent)
        )
        val success = assertIs<CompileResult.Success>(
            engine.compile(resolution.requirements)
        )

        assertEquals(
            "xiao_esp32s3",
            success.bundle.designIr.board.boardId,
        )
        assertEquals(10, success.bundle.circuitGraph.connections.size)
        assertTrue(success.bundle.diagramSpec.buildPlan?.steps?.size == 10)
        assertTrue(success.bundle.manifest != null)
        assertTrue(success.bundle.uiSpec.pages.any { it.id == "dashboard" })
        assertTrue(success.bundle.uiSpec.pages.any { it.id == "settings" })
        assertTrue(success.bundle.uiSpec.pages.any { it.id == "history" })
        assertTrue(success.bundle.testPlan.tests.isNotEmpty())
    }

    @Test
    fun `researched DHT11 compiles through wiring and runtime manifest`() {
        val profile =
            RuntimeDriverProfile(
                driverId =
                    "profile_dht_pulse_sensor_aosong_dht11",
                family =
                    RuntimeDriverFamily.DHT_PULSE_SENSOR,
                interfaceType =
                    ElectricalInterface.ONE_WIRE,
                sampleIntervalMs = 2000,
                parameters =
                    mapOf(
                        "variant" to "DHT11",
                        "start_low_us" to "18000",
                        "zero_high_max_us" to "40",
                        "one_high_min_us" to "60",
                    ),
                telemetry =
                    listOf(
                        RuntimeDriverTelemetrySpec(
                            id = "temperature",
                            unit = "C",
                            source = "DHT_TEMPERATURE",
                        ),
                        RuntimeDriverTelemetrySpec(
                            id = "humidity",
                            unit = "%",
                            source = "DHT_HUMIDITY",
                        ),
                    ),
                status =
                    RuntimeDriverProfileStatus.RUNTIME_READY,
            )
        val dht11 =
            ComponentSpec(
                componentId =
                    "research_aosong_dht11",
                displayName = "DHT11",
                kind = ComponentKind.SENSOR,
                defaultRole = "sensor_dht11",
                providesCapabilities =
                    setOf(
                        CapabilityId("measure_temperature"),
                        CapabilityId("measure_humidity"),
                    ),
                primaryInterface =
                    ElectricalInterface.ONE_WIRE,
                voltageRange =
                    VoltageRange(3.0, 3.3, 5.5),
                preferredSupplyVoltageV = 3.3,
                supplyRole = SupplyRole.LOGIC,
                currentMaxMa = 2.5,
                driverId = profile.driverId,
                runtimeDriverProfile = profile,
                designReady = true,
                pins =
                    listOf(
                        ComponentPinSpec(
                            "cp_dht11_vcc",
                            "VCC",
                            ComponentPinRole.VCC,
                        ),
                        ComponentPinSpec(
                            "cp_dht11_data",
                            "DATA",
                            ComponentPinRole.DATA,
                        ),
                        ComponentPinSpec(
                            "cp_dht11_gnd",
                            "GND",
                            ComponentPinRole.GND,
                        ),
                    ),
                signalRequirements =
                    listOf(
                        SignalRequirement(
                            id = "data",
                            boardCapability =
                                BoardPinCapability.DIGITAL_IO,
                            componentPinRole =
                                ComponentPinRole.DATA,
                            netType = NetType.DIGITAL,
                            wireSemantic =
                                WireSemantic.SIGNAL,
                        )
                    ),
                aliases = setOf("DHT11", "DHT-11"),
                verificationStatus =
                    ComponentVerificationStatus.DESIGN_READY,
            )

        val catalog =
            CompositeEngineeringCatalog(
                base = GoldenEngineeringCatalog,
                researched =
                    object : ResearchedComponentSource {
                        override fun researchedComponents() =
                            listOf(dht11)
                    },
            )
        val researchedEngine =
            ApplicationProjectEngine(catalog)

        val result =
            researchedEngine.compile(
                ResolvedRequirements(
                    goal = "DHT11で温湿度を測定する",
                    slots = emptyMap(),
                    requestedComponents =
                        listOf(
                            RequestedComponent(
                                rawName = "DHT11",
                                categoryHint = "sensor",
                            )
                        ),
                )
            )

        val success =
            assertIs<CompileResult.Success>(result)
        assertEquals(
            "xiao_esp32s3",
            success.bundle.designIr.board.boardId,
        )
        assertTrue(
            success.bundle.circuitGraph.connections.any {
                it.to.entityId == "sensor_dht11" ||
                    it.from.entityId == "sensor_dht11"
            }
        )
        val manifest =
            requireNotNull(success.bundle.manifest)
        assertEquals(
            profile.driverId,
            manifest.driverProfiles.single().driverId,
        )
        assertEquals(
            "4",
            manifest.devices
                .single {
                    it.instanceId == "sensor_dht11"
                }
                .config["gpio"],
        )
        assertTrue(
            success.bundle.diagramSpec.placements.any {
                it.entityId == "sensor_dht11"
            }
        )
    }

    @Test
    fun `temperature threshold is extracted without asking GPIO or library questions`() {
        val intent = interpreter.interpret(
            "温度が32℃以上になったらファンを回したい"
        )

        val resolution = assertIs<RequirementResolution.Ready>(
            engine.resolve(intent)
        )

        assertEquals(
            "32.0",
            resolution.requirements.slots["temp_on"]?.value,
        )
        assertEquals(
            "30.0",
            resolution.requirements.slots["temp_off"]?.value,
        )

        val allSlots = resolution.requirements.slots.keys
        assertTrue(allSlots.none { it.contains("gpio", ignoreCase = true) })
        assertTrue(allSlots.none { it.contains("library", ignoreCase = true) })
    }

    @Test
    fun `combined Japanese temperature humidity term maps both sensors`() {
        val intent = interpreter.interpret(
            "温湿度を記録し、30℃以上でファン、28℃以下で停止。"
        )
        val resolution = assertIs<RequirementResolution.Ready>(
            engine.resolve(intent)
        )
        val success = assertIs<CompileResult.Success>(
            engine.compile(resolution.requirements)
        )

        val capabilities = success.bundle.designIr.capabilities.map { it.value }.toSet()
        assertTrue("measure_temperature" in capabilities)
        assertTrue("measure_humidity" in capabilities)
        assertEquals(
            "28.0",
            success.bundle.designIr.settings
                .single { it.id == "temp_off" }
                .defaultValue,
        )
    }
}
