package com.aielectronics.bench

enum class BenchStepId {
    HANDSHAKE,
    DEPLOY,
    VERIFY,
    SELF_TESTS,
    TELEMETRY,
    SETTINGS_ROUNDTRIP,
}

enum class BenchStepStatus {
    PENDING,
    RUNNING,
    PASS,
    FAIL,
    SKIPPED,
}

data class BenchStepResult(
    val id: BenchStepId,
    val status: BenchStepStatus,
    val message: String,
)

data class BenchReport(
    val projectId: String,
    val steps: List<BenchStepResult>,
    val telemetry: Map<String, String> = emptyMap(),
    val testedSettingId: String? = null,
) {
    val passed: Boolean
        get() = steps.isNotEmpty() &&
            steps.all { it.status == BenchStepStatus.PASS }
}

data class BenchGateState(
    val running: Boolean = false,
    val report: BenchReport? = null,
    val error: String? = null,
)
