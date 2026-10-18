package com.aielectronics.core.model

enum class CoreOperationMode {
    AUTONOMOUS_MCU,
    EXTERNAL_INPUT_REQUIRED,
}

data class OfflineAutonomySpec(
    val coreOperationMode: CoreOperationMode = CoreOperationMode.AUTONOMOUS_MCU,
    val localBehaviorExecutionRequired: Boolean = true,
    val localSafetyExecutionRequired: Boolean = true,
    val persistRuntimeSettings: Boolean = true,
    val externalInputReason: String? = null,
)
