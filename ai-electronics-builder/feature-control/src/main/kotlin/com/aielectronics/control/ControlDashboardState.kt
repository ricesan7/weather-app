package com.aielectronics.control

enum class DeviceConnectionState {
    CONNECTED,
    DISCONNECTED,
    ERROR,
}

enum class TestRunState {
    IDLE,
    RUNNING,
    PASSED,
    FAILED,
}

data class ChartSample(
    val sequence: Long,
    val value: Double,
)

data class ControlDashboardState(
    val connection: DeviceConnectionState = DeviceConnectionState.DISCONNECTED,
    val telemetry: Map<String, String> = emptyMap(),
    val settings: Map<String, String> = emptyMap(),
    val history: Map<String, List<ChartSample>> = emptyMap(),
    val testResults: Map<String, TestRunState> = emptyMap(),
    val pendingSettingIds: Set<String> = emptySet(),
    val refreshing: Boolean = false,
    val lastError: String? = null,
) {
    fun value(binding: String): String? = when {
        binding.startsWith("telemetry.") ->
            telemetry[binding.removePrefix("telemetry.")]

        binding.startsWith("settings.") ->
            settings[binding.removePrefix("settings.")]

        binding.startsWith("controls.") ->
            settings[binding.removePrefix("controls.")]

        binding.startsWith("logging.") ->
            history[binding.removePrefix("logging.")]
                ?.lastOrNull()
                ?.value
                ?.toString()

        else -> telemetry[binding] ?: settings[binding]
    }

    fun history(binding: String): List<ChartSample> =
        history[
            binding
                .removePrefix("logging.")
                .removePrefix("telemetry.")
        ].orEmpty()
}
