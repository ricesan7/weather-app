package com.aielectronics.core.model

data class SoftwarePlan(
    val companionSoftwareRequired: Boolean = false,
    val base44DesignRequired: Boolean = false,
    val hardwareBridgeRequired: Boolean = false,
    val reasons: List<String> = emptyList(),
    val deviceBridge: DeviceBridgeSpec? = null,
    val base44Handoff: Base44HandoffSpec? = null,
)

enum class BridgeTransport {
    BLE,
    USB,
    WIFI,
}

enum class BridgeValueType {
    BOOLEAN,
    NUMBER,
    ENUM,
    TEXT,
    ACTION,
}

data class DeviceBridgeSpec(
    val schemaVersion: String = "1.0",
    val transport: BridgeTransport,
    val commands: List<DeviceBridgeCommand> = emptyList(),
    val telemetry: List<DeviceBridgeTelemetry> = emptyList(),
    val events: List<DeviceBridgeEvent> = emptyList(),
)

data class DeviceBridgeCommand(
    val id: String,
    val binding: String,
    val valueType: BridgeValueType,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val allowedValues: List<String> = emptyList(),
)

data class DeviceBridgeTelemetry(
    val id: String,
    val binding: String,
    val valueType: BridgeValueType,
    val unit: String? = null,
)

data class DeviceBridgeEvent(
    val id: String,
    val severity: Severity,
)

data class Base44HandoffSpec(
    val schemaVersion: String = "1.0",
    val projectId: String,
    val projectName: String,
    val goal: String,
    val uiSpec: UiSpec,
    val bridge: DeviceBridgeSpec,
    val applicationResponsibilities: List<String> = listOf(
        "UI and UX",
        "user authentication",
        "cloud data and history",
        "automation and AI features",
    ),
    val hardwareBridgeResponsibilities: List<String> = listOf(
        "Android permissions",
        "BLE, USB, and Wi-Fi device access",
        "device command translation",
        "telemetry transport",
        "firmware deployment and diagnostics",
    ),
)
