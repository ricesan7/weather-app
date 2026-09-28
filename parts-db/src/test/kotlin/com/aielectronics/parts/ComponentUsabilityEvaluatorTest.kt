package com.aielectronics.parts

import com.aielectronics.core.model.CapabilityId
import com.aielectronics.core.model.ComponentKind
import com.aielectronics.core.model.ComponentPinRole
import com.aielectronics.core.model.ComponentPinSpec
import com.aielectronics.core.model.ElectricalInterface
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComponentUsabilityEvaluatorTest {
    private val authoritativeSource = ComponentSourceRecord(
        url = "https://example.com/manufacturer-datasheet.pdf",
        title = "Manufacturer datasheet",
        authority = SourceAuthority.MANUFACTURER_DATASHEET,
    )

    private val defaultRuntime = RuntimeSupportRegistry(
        supportedDriverIds = setOf("drv_sht31"),
        supportedProfileFamilies = setOf(
            RuntimeDriverFamily.GPIO_DIGITAL_INPUT,
            RuntimeDriverFamily.GPIO_DIGITAL_OUTPUT,
            RuntimeDriverFamily.I2C_REGISTER_SENSOR,
        ),
    )

    private val evaluator = DefaultComponentUsabilityEvaluator(defaultRuntime)

    @Test
    fun `authoritative i2c sensor with complete runtime information is ready`() {
        val report = evaluator.evaluate(
            candidate = i2cSensor(),
            requiredCapabilities = setOf(CapabilityId("measure_temperature")),
        )

        assertEquals(ComponentUsabilityStatus.READY, report.status)
        assertTrue(report.missingFields.isEmpty())
        assertTrue(report.blockingReasons.isEmpty())
    }

    @Test
    fun `i2c sensor missing address needs exact information`() {
        val report = evaluator.evaluate(
            candidate = i2cSensor(i2cAddress = null),
            requiredCapabilities = setOf(CapabilityId("measure_temperature")),
        )

        assertEquals(ComponentUsabilityStatus.NEEDS_INFO, report.status)
        assertTrue("i2c_address" in report.missingFields)
    }

    @Test
    fun `load without maximum current needs information`() {
        val report = evaluator.evaluate(
            candidate = ComponentResearchCandidate(
                requestedName = "5V fan",
                displayName = "5V fan",
                kind = ComponentKind.ACTUATOR,
                primaryInterface = ElectricalInterface.GPIO,
                voltageMinV = 4.5,
                voltageTypicalV = 5.0,
                voltageMaxV = 5.5,
                currentMaxMa = null,
                requiresExternalPower = true,
                capabilities = setOf(CapabilityId("actuate_fan")),
                pins = listOf(
                    ComponentPinSpec("positive", "+", ComponentPinRole.POSITIVE),
                    ComponentPinSpec("negative", "-", ComponentPinRole.NEGATIVE),
                ),
                driverId = "drv_gpio_sink",
                sources = listOf(authoritativeSource),
            ),
            requiredCapabilities = setOf(CapabilityId("actuate_fan")),
        )

        assertEquals(ComponentUsabilityStatus.NEEDS_INFO, report.status)
        assertTrue("current_max_ma" in report.missingFields)
    }

    @Test
    fun `complete component with unsupported runtime profile is unsupported`() {
        val report = evaluator.evaluate(
            candidate = i2cSensor(
                driverId = null,
                driverProfile = RuntimeDriverProfile(
                    family = RuntimeDriverFamily.DHT_PULSE_SENSOR,
                ),
            ),
            requiredCapabilities = setOf(CapabilityId("measure_temperature")),
        )

        assertEquals(ComponentUsabilityStatus.UNSUPPORTED, report.status)
        assertTrue(report.blockingReasons.any { it.contains("runtime", ignoreCase = true) })
    }

    @Test
    fun `supported dht pulse profile can be ready when timing is authoritative`() {
        val dhtEvaluator = DefaultComponentUsabilityEvaluator(
            RuntimeSupportRegistry(
                supportedProfileFamilies = setOf(RuntimeDriverFamily.DHT_PULSE_SENSOR),
            )
        )
        val candidate = ComponentResearchCandidate(
            requestedName = "DHT22",
            manufacturer = "Aosong",
            model = "DHT22",
            displayName = "DHT22",
            kind = ComponentKind.SENSOR,
            primaryInterface = ElectricalInterface.ONE_WIRE,
            voltageMinV = 3.3,
            voltageTypicalV = 5.0,
            voltageMaxV = 6.0,
            capabilities = setOf(
                CapabilityId("measure_temperature"),
                CapabilityId("measure_humidity"),
            ),
            pins = listOf(
                ComponentPinSpec("vcc", "VCC", ComponentPinRole.VCC),
                ComponentPinSpec("data", "DATA", ComponentPinRole.DATA),
                ComponentPinSpec("gnd", "GND", ComponentPinRole.GND),
            ),
            driverProfile = RuntimeDriverProfile(
                family = RuntimeDriverFamily.DHT_PULSE_SENSOR,
                sampleIntervalMs = 2000,
                parameters = mapOf(
                    "variant" to "DHT22",
                    "start_low_us" to "1000",
                    "zero_high_max_us" to "40",
                    "one_high_min_us" to "60",
                ),
            ),
            sources = listOf(authoritativeSource),
        )

        val report = dhtEvaluator.evaluate(
            candidate,
            setOf(
                CapabilityId("measure_temperature"),
                CapabilityId("measure_humidity"),
            ),
        )

        assertEquals(ComponentUsabilityStatus.READY, report.status)
    }

    @Test
    fun `weak sources cannot authorize critical electrical facts`() {
        val weak = i2cSensor(
            sources = listOf(
                ComponentSourceRecord(
                    url = "https://example.com/blog",
                    title = "Blog",
                    authority = SourceAuthority.OTHER,
                )
            )
        )

        val report = evaluator.evaluate(
            weak,
            setOf(CapabilityId("measure_temperature")),
        )

        assertEquals(ComponentUsabilityStatus.NEEDS_INFO, report.status)
        assertTrue("authoritative_source" in report.missingFields)
    }

    @Test
    fun `hazardous energy candidate is unsupported by low voltage admission`() {
        val candidate = ComponentResearchCandidate(
            requestedName = "200V VFD inverter",
            displayName = "200V VFD inverter",
            kind = ComponentKind.OTHER,
            primaryInterface = ElectricalInterface.RS485,
            voltageMinV = 180.0,
            voltageTypicalV = 200.0,
            voltageMaxV = 240.0,
            capabilities = setOf(CapabilityId("actuate_motor")),
            driverProfile = RuntimeDriverProfile(RuntimeDriverFamily.NONE),
            sources = listOf(authoritativeSource),
        )

        val report = evaluator.evaluate(
            candidate,
            setOf(CapabilityId("actuate_motor")),
        )

        assertEquals(ComponentUsabilityStatus.UNSUPPORTED, report.status)
        assertTrue(report.blockingReasons.any { it.contains("high-energy", ignoreCase = true) })
    }

    private fun i2cSensor(
        i2cAddress: String? = "0x44",
        driverId: String? = "drv_sht31",
        driverProfile: RuntimeDriverProfile? = null,
        sources: List<ComponentSourceRecord> = listOf(authoritativeSource),
    ) = ComponentResearchCandidate(
        requestedName = "SHT31",
        manufacturer = "Sensirion",
        model = "SHT31",
        displayName = "SHT31",
        kind = ComponentKind.SENSOR,
        primaryInterface = ElectricalInterface.I2C,
        voltageMinV = 2.4,
        voltageTypicalV = 3.3,
        voltageMaxV = 5.5,
        preferredSupplyV = 3.3,
        i2cAddress = i2cAddress,
        capabilities = setOf(CapabilityId("measure_temperature")),
        pins = listOf(
            ComponentPinSpec("vcc", "VCC", ComponentPinRole.VCC),
            ComponentPinSpec("gnd", "GND", ComponentPinRole.GND),
            ComponentPinSpec("sda", "SDA", ComponentPinRole.I2C_SDA),
            ComponentPinSpec("scl", "SCL", ComponentPinRole.I2C_SCL),
        ),
        driverId = driverId,
        driverProfile = driverProfile,
        sources = sources,
    )
}
