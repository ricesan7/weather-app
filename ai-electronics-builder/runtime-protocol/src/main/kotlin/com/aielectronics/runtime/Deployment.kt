package com.aielectronics.runtime

import com.aielectronics.core.model.ReleaseBundle

enum class DeployState {
    IDLE, IDENTIFY, PLAN, PREFLIGHT, RUNTIME_CHECK, TRANSFER, VERIFY, SELF_TEST, READY, FAILED,
}

data class DeviceInfo(
    val deviceId: String,
    val boardId: String,
    val runtimeVersion: String?,
    val transports: Set<String>,
)

data class DeployPlan(
    val device: DeviceInfo,
    val strategy: DeployStrategy,
    val requiresRuntimeInstall: Boolean,
    val bundle: ReleaseBundle,
)

enum class DeployStrategy { MANIFEST_ONLY, GENERATED_FIRMWARE }

data class DeployProgress(
    val state: DeployState,
    val percent: Int,
    val userMessage: String,
    val technicalDetail: String? = null,
)

sealed interface DeployResult {
    data class Success(val device: DeviceInfo) : DeployResult
    data class Failed(val state: DeployState, val userMessage: String, val technicalCode: String?) : DeployResult
}

interface DeploymentPlanner {
    fun plan(bundle: ReleaseBundle, device: DeviceInfo): Result<DeployPlan>
}

interface DeployManager {
    fun deploy(plan: DeployPlan, onProgress: (DeployProgress) -> Unit): DeployResult
}
