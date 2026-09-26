package com.aielectronics.parts

import com.aielectronics.core.model.*

object GoldenEngineeringCatalog : EngineeringCatalog {

    private val componentSpecs = listOf(
        ComponentSpec(
            componentId = "ae_sht31",
            displayName = "AE-SHT31",
            kind = ComponentKind.SENSOR,
            defaultRole = "environment_sensor",
            providesCapabilities = setOf(
                CapabilityId("measure_temperature"),
                CapabilityId("measure_humidity"),
            ),
            primaryInterface = ElectricalInterface.I2C,
            voltageRange = VoltageRange(2.4, 3.3, 5.5),
            preferredSupplyVoltageV = 3.3,
            supplyRole = SupplyRole.LOGIC,
            currentMaxMa = null,
            driverId = "drv_sensirion_i2c_sht3x_arduino",
            designReady = true,
            engineeringPriority = 100,
        ),
        ComponentSpec(
            componentId = "fan_ydm2510c05",
            displayName = "YDM2510C05 5V Fan",
            kind = ComponentKind.ACTUATOR,
            defaultRole = "fan",
            providesCapabilities = setOf(CapabilityId("actuate_fan")),
            voltageRange = VoltageRange(4.5, 5.0, 5.5),
            preferredSupplyVoltageV = 5.0,
            supplyRole = SupplyRole.LOAD,
            currentMaxMa = 140.0,
            requiresExternalPower = true,
            requiredSupportTags = setOf("low_side_driver_3v3"),
            designReady = true,
            engineeringPriority = 100,
        ),
        ComponentSpec(
            componentId = "tbd62003apg",
            displayName = "TBD62003APG",
            kind = ComponentKind.DRIVER,
            defaultRole = "load_driver",
            primaryInterface = ElectricalInterface.GPIO,
            supplyRole = SupplyRole.NONE,
            tags = setOf("low_side_driver_3v3"),
            designReady = true,
            engineeringPriority = 100,
        ),
    )

    private val boardSpecs = listOf(
        BoardSpec(
            boardId = "xiao_esp32s3",
            displayName = "Seeed Studio XIAO ESP32S3",
            logicVoltageV = 3.3,
            supportedInterfaces = setOf(
                ElectricalInterface.GPIO,
                ElectricalInterface.I2C,
                ElectricalInterface.SPI,
                ElectricalInterface.UART,
                ElectricalInterface.PWM,
                ElectricalInterface.ADC,
                ElectricalInterface.USB,
            ),
            transports = setOf(TransportKind.BLE, TransportKind.WIFI, TransportKind.USB),
            providedRailsV = setOf(3.3, 5.0),
            designReady = true,
            beginnerPriority = 100,
        ),
        BoardSpec(
            boardId = "esp32_s3_devkitc1_n8r8",
            displayName = "ESP32-S3-DevKitC-1-N8R8",
            logicVoltageV = 3.3,
            supportedInterfaces = setOf(
                ElectricalInterface.GPIO,
                ElectricalInterface.I2C,
                ElectricalInterface.SPI,
                ElectricalInterface.UART,
                ElectricalInterface.PWM,
                ElectricalInterface.ADC,
                ElectricalInterface.USB,
            ),
            transports = setOf(TransportKind.BLE, TransportKind.WIFI, TransportKind.USB),
            providedRailsV = setOf(3.3, 5.0),
            designReady = true,
            beginnerPriority = 80,
        ),
    )

    private val supplySpecs = listOf(
        PowerSupplySpec(
            supplyId = "supply_ad_t50p200_5v2a",
            componentId = "psu_ad_t50p200_5v2a",
            displayName = "AD-T50P200 5V 2A",
            outputVoltageV = 5.0,
            maxCurrentMa = 2000.0,
            polarity = Polarity.CENTER_POSITIVE,
            designReady = true,
            engineeringPriority = 100,
        )
    )

    override fun components(): List<ComponentSpec> = componentSpecs
    override fun boards(): List<BoardSpec> = boardSpecs
    override fun powerSupplies(): List<PowerSupplySpec> = supplySpecs
}
