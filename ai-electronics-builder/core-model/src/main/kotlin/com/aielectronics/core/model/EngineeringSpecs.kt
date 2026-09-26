package com.aielectronics.core.model

enum class ElectricalInterface {
    GPIO,
    I2C,
    SPI,
    UART,
    PWM,
    ADC,
    USB,
}

enum class ComponentKind {
    SENSOR,
    ACTUATOR,
    DRIVER,
    DISPLAY,
    POWER_SUPPLY,
    LEVEL_SHIFTER,
    STORAGE,
    OTHER,
}

enum class SupplyRole {
    LOGIC,
    LOAD,
    NONE,
}

enum class BoardPinCapability {
    DIGITAL_OUT,
    PWM,
    I2C_SDA,
    I2C_SCL,
    POWER_3V3,
    POWER_5V,
    GND,
}

enum class ComponentPinRole {
    VCC,
    GND,
    I2C_SDA,
    I2C_SCL,
    ADDRESS,
    CONTROL_INPUT,
    LOAD_OUTPUT,
    CLAMP_COMMON,
    POSITIVE,
    NEGATIVE,
}

data class VoltageRange(
    val minV: Double,
    val typicalV: Double,
    val maxV: Double,
)

data class BoardPinSpec(
    val pinId: String,
    val label: String,
    val gpioNumber: Int? = null,
    val capabilities: Set<BoardPinCapability>,
    val allocationPriority: Int = 0,
)

data class ComponentPinSpec(
    val pinId: String,
    val label: String,
    val role: ComponentPinRole,
)

data class SignalRequirement(
    val id: String,
    val boardCapability: BoardPinCapability,
    val componentPinRole: ComponentPinRole,
    val netType: NetType,
    val wireSemantic: WireSemantic,
    val shareable: Boolean = false,
)

data class ComponentSpec(
    val componentId: String,
    val displayName: String,
    val kind: ComponentKind,
    val defaultRole: String,
    val providesCapabilities: Set<CapabilityId> = emptySet(),
    val primaryInterface: ElectricalInterface? = null,
    val voltageRange: VoltageRange? = null,
    val preferredSupplyVoltageV: Double? = null,
    val supplyRole: SupplyRole = SupplyRole.NONE,
    val currentMaxMa: Double? = null,
    val requiresExternalPower: Boolean = false,
    val requiredSupportTags: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val driverId: String? = null,
    val designReady: Boolean = false,
    val engineeringPriority: Int = 0,
    val pins: List<ComponentPinSpec> = emptyList(),
    val signalRequirements: List<SignalRequirement> = emptyList(),
    val i2cAddress: String? = null,
    val maxLoadCurrentMa: Double? = null,
    val minInputHighVoltageV: Double? = null,
    val sourceIds: Set<String> = emptySet(),
)

data class BoardSpec(
    val boardId: String,
    val displayName: String,
    val logicVoltageV: Double,
    val supportedInterfaces: Set<ElectricalInterface>,
    val transports: Set<TransportKind>,
    val providedRailsV: Set<Double>,
    val designReady: Boolean,
    val beginnerPriority: Int,
    val pins: List<BoardPinSpec> = emptyList(),
    val sourceIds: Set<String> = emptySet(),
)

data class PowerSupplySpec(
    val supplyId: String,
    val componentId: String,
    val displayName: String,
    val outputVoltageV: Double,
    val maxCurrentMa: Double,
    val polarity: Polarity,
    val designReady: Boolean,
    val engineeringPriority: Int,
    val sourceIds: Set<String> = emptySet(),
)
