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

enum class AppBridgeDirection {
    HARDWARE_TO_BASE44,
    BASE44_TO_HARDWARE,
    HARDWARE_EVENT_TO_BASE44,
}

enum class AppBridgePresentation {
    VALUE,
    GAUGE,
    STATUS,
    TOGGLE,
    SLIDER,
    SELECT,
    BUTTON,
    EVENT,
}

data class AppBridgeChannel(
    val id: String,
    val binding: String,
    val direction: AppBridgeDirection,
    val valueType: BridgeValueType,
    val displayName: String? = null,
    val presentation: AppBridgePresentation = AppBridgePresentation.VALUE,
    val unit: String? = null,
    val min: Double? = null,
    val max: Double? = null,
    val step: Double? = null,
    val allowedValues: List<String> = emptyList(),
)

data class AppHardwareIntegrationContract(
    val schemaVersion: String = "1.0",
    val channels: List<AppBridgeChannel> = emptyList(),
)

enum class Base44ApplicationCapability {
    LIVE_DATA,
    HISTORY,
    DEVICE_CONTROL,
    DEVICE_SETTINGS,
    NOTIFICATIONS,
    REMOTE_ACCESS,
    AUTOMATION,
    AI_FEATURES,
}

data class Base44HandoffSpec(
    val schemaVersion: String = "1.0",
    val projectId: String,
    val projectName: String,
    val goal: String,
    val uiSpec: UiSpec,
    val bridge: DeviceBridgeSpec,
    val integration: AppHardwareIntegrationContract,
    val requestedCapabilities: Set<Base44ApplicationCapability> = emptySet(),
    val applicationResponsibilities: List<String> = listOf(
        "UI and UX",
        "application state and workflows",
        "optional authentication and cloud features",
        "history, notifications, automation, and AI when requested",
    ),
    val hardwareBridgeResponsibilities: List<String> = listOf(
        "Android permissions",
        "BLE, USB, and Wi-Fi device access",
        "device command translation",
        "telemetry and event forwarding",
        "firmware deployment and diagnostics",
    ),
)
