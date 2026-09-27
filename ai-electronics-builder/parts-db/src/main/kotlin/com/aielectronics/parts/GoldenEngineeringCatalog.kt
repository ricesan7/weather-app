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
            pins = listOf(
                ComponentPinSpec("cp_ae_sht31_1", "VDD", ComponentPinRole.VCC),
                ComponentPinSpec("cp_ae_sht31_2", "SDA", ComponentPinRole.I2C_SDA),
                ComponentPinSpec("cp_ae_sht31_3", "SCL", ComponentPinRole.I2C_SCL),
                ComponentPinSpec("cp_ae_sht31_4", "ADR", ComponentPinRole.ADDRESS),
                ComponentPinSpec("cp_ae_sht31_5", "GND", ComponentPinRole.GND),
            ),
            signalRequirements = listOf(
                SignalRequirement(
                    id = "sda",
                    boardCapability = BoardPinCapability.I2C_SDA,
                    componentPinRole = ComponentPinRole.I2C_SDA,
                    netType = NetType.I2C_SDA,
                    wireSemantic = WireSemantic.SIGNAL,
                    shareable = true,
                ),
                SignalRequirement(
                    id = "scl",
                    boardCapability = BoardPinCapability.I2C_SCL,
                    componentPinRole = ComponentPinRole.I2C_SCL,
                    netType = NetType.I2C_SCL,
                    wireSemantic = WireSemantic.SIGNAL,
                    shareable = true,
                ),
            ),
            i2cAddress = "0x45",
            sourceIds = setOf(
                "src_akizuki_ae_sht31_manual",
                "src_sensirion_sht3x_datasheet",
            ),
            aliases = setOf("SHT31", "SHT3x", "AE-SHT31"),
            verificationStatus =
                ComponentVerificationStatus.DESIGN_READY,
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
            pins = listOf(
                ComponentPinSpec("cp_fan_ydm2510_red", "RED +", ComponentPinRole.POSITIVE),
                ComponentPinSpec("cp_fan_ydm2510_black", "BLACK -", ComponentPinRole.NEGATIVE),
            ),
            sourceIds = setOf("src_yccfan_ydm2510c05_datasheet"),
            aliases = setOf("YDM2510C05"),
            verificationStatus =
                ComponentVerificationStatus.DESIGN_READY,
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
            pins = listOf(
                ComponentPinSpec("cp_tbd62003_1", "I1", ComponentPinRole.CONTROL_INPUT),
                ComponentPinSpec("cp_tbd62003_16", "O1", ComponentPinRole.LOAD_OUTPUT),
                ComponentPinSpec("cp_tbd62003_8", "GND", ComponentPinRole.GND),
                ComponentPinSpec("cp_tbd62003_9", "COMMON", ComponentPinRole.CLAMP_COMMON),
            ),
            signalRequirements = listOf(
                SignalRequirement(
                    id = "control",
                    boardCapability = BoardPinCapability.DIGITAL_OUT,
                    componentPinRole = ComponentPinRole.CONTROL_INPUT,
                    netType = NetType.CONTROL,
                    wireSemantic = WireSemantic.CONTROL,
                )
            ),
            maxLoadCurrentMa = 500.0,
            minInputHighVoltageV = 2.5,
            sourceIds = setOf("src_toshiba_tbd62003"),
            aliases = setOf("TBD62003", "TBD62003APG"),
            verificationStatus =
                ComponentVerificationStatus.DESIGN_READY,
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
            pins = listOf(
                BoardPinSpec(
                    "pin_xiao_d0_gpio1",
                    "D0 / GPIO1",
                    1,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_d1_gpio2",
                    "D1 / GPIO2",
                    2,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_d2_gpio3",
                    "D2 / GPIO3",
                    3,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_d3_gpio4",
                    "D3 / GPIO4",
                    4,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_d4_gpio5",
                    "D4 / GPIO5 / SDA",
                    5,
                    setOf(BoardPinCapability.I2C_SDA),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_d5_gpio6",
                    "D5 / GPIO6 / SCL",
                    6,
                    setOf(BoardPinCapability.I2C_SCL),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_d6_gpio43",
                    "D6 / GPIO43 / TX",
                    43,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    90,
                ),
                BoardPinSpec(
                    "pin_xiao_d7_gpio44",
                    "D7 / GPIO44 / RX",
                    44,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    90,
                ),
                BoardPinSpec(
                    "pin_xiao_d8_gpio7",
                    "D8 / GPIO7",
                    7,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    95,
                ),
                BoardPinSpec(
                    "pin_xiao_d9_gpio8",
                    "D9 / GPIO8",
                    8,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    95,
                ),
                BoardPinSpec(
                    "pin_xiao_d10_gpio9",
                    "D10 / GPIO9",
                    9,
                    setOf(
                        BoardPinCapability.DIGITAL_IN,
                        BoardPinCapability.DIGITAL_OUT,
                        BoardPinCapability.DIGITAL_IO,
                        BoardPinCapability.PWM,
                    ),
                    95,
                ),
                BoardPinSpec(
                    "pin_xiao_3v3",
                    "3V3",
                    null,
                    setOf(BoardPinCapability.POWER_3V3),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_5v",
                    "5V",
                    null,
                    setOf(BoardPinCapability.POWER_5V),
                    100,
                ),
                BoardPinSpec(
                    "pin_xiao_gnd",
                    "GND",
                    null,
                    setOf(BoardPinCapability.GND),
                    100,
                ),
            ),
            sourceIds = setOf("src_seeed_xiao_esp32s3"),
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
            pins = listOf(
                BoardPinSpec(
                    "pin_devkit_gpio18",
                    "GPIO18",
                    18,
                    setOf(BoardPinCapability.DIGITAL_OUT, BoardPinCapability.PWM),
                    100,
                ),
                BoardPinSpec(
                    "pin_devkit_gpio8",
                    "GPIO8 / SDA",
                    8,
                    setOf(BoardPinCapability.I2C_SDA),
                    100,
                ),
                BoardPinSpec(
                    "pin_devkit_gpio9",
                    "GPIO9 / SCL",
                    9,
                    setOf(BoardPinCapability.I2C_SCL),
                    100,
                ),
                BoardPinSpec("pin_devkit_3v3", "3V3", null, setOf(BoardPinCapability.POWER_3V3), 100),
                BoardPinSpec("pin_devkit_5v", "5V", null, setOf(BoardPinCapability.POWER_5V), 100),
                BoardPinSpec("pin_devkit_gnd", "GND", null, setOf(BoardPinCapability.GND), 100),
            ),
            sourceIds = setOf("src_espressif_devkitc1"),
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
