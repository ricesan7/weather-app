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

data class VoltageRange(
    val minV: Double,
    val typicalV: Double,
    val maxV: Double,
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
)
