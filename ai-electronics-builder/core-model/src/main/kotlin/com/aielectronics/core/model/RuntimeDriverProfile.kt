package com.aielectronics.core.model

enum class RuntimeDriverFamily {
    GPIO_DIGITAL_INPUT,
    GPIO_DIGITAL_OUTPUT,
    DHT_PULSE_SENSOR,
    I2C_REGISTER_SENSOR,
}

enum class RuntimeDriverProfileStatus {
    GENERATED,
    VALIDATED,
    RUNTIME_READY,
    REJECTED,
}

data class RuntimeDriverTelemetrySpec(
    val id: String,
    val unit: String = "",
    val source: String,
    val scale: Double = 1.0,
    val offset: Double = 0.0,
)

data class RuntimeDriverProfile(
    val driverId: String,
    val family: RuntimeDriverFamily,
    val interfaceType: ElectricalInterface,
    val sampleIntervalMs: Int = 1000,
    val parameters: Map<String, String> = emptyMap(),
    val telemetry: List<RuntimeDriverTelemetrySpec> = emptyList(),
    val sourceIds: Set<String> = emptySet(),
    val status: RuntimeDriverProfileStatus =
        RuntimeDriverProfileStatus.GENERATED,
) {
    val runtimeReady: Boolean
        get() = status == RuntimeDriverProfileStatus.RUNTIME_READY
}
