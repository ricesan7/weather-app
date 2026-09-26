package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultUiCompiler : UiCompiler {

    override fun compile(core: DesignCore): Result<UiSpec> = runCatching {
        val capabilities = core.capabilities.map { it.value }.toSet()

        val dashboardWidgets = mutableListOf<UiWidget>()

        if ("measure_temperature" in capabilities) {
            dashboardWidgets += UiWidget.ValueCard(
                id = "temperature_value",
                binding = "telemetry.temperature",
                unit = "°C",
            )
            dashboardWidgets += UiWidget.Gauge(
                id = "temperature_gauge",
                binding = "telemetry.temperature",
                min = 0.0,
                max = 50.0,
            )
        }

        if ("measure_humidity" in capabilities) {
            dashboardWidgets += UiWidget.ValueCard(
                id = "humidity_value",
                binding = "telemetry.humidity",
                unit = "%",
            )
            dashboardWidgets += UiWidget.Gauge(
                id = "humidity_gauge",
                binding = "telemetry.humidity",
                min = 0.0,
                max = 100.0,
            )
        }

        if ("measure_pressure" in capabilities) {
            dashboardWidgets += UiWidget.ValueCard(
                id = "pressure_value",
                binding = "telemetry.pressure",
                unit = "hPa",
            )
            dashboardWidgets += UiWidget.Gauge(
                id = "pressure_gauge",
                binding = "telemetry.pressure",
                min = 260.0,
                max = 1260.0,
            )
        }

        if ("measure_illuminance" in capabilities) {
            dashboardWidgets += UiWidget.ValueCard(
                id = "illuminance_value",
                binding = "telemetry.illuminance",
                unit = "lx",
            )
            dashboardWidgets += UiWidget.Gauge(
                id = "illuminance_gauge",
                binding = "telemetry.illuminance",
                min = 0.0,
                max = 16768.0,
            )
        }

        if ("actuate_fan" in capabilities) {
            dashboardWidgets += UiWidget.Status(
                id = "fan_status",
                binding = "telemetry.fan_state",
            )
        }

        core.settings.firstOrNull { it.id == "mode" }?.let { setting ->
            dashboardWidgets += UiWidget.Select(
                id = "mode",
                binding = "settings.mode",
                options = setting.constraints.allowedValues,
            )
        }

        core.settings.firstOrNull { it.id == "manual_fan" }?.let {
            dashboardWidgets += UiWidget.Toggle(
                id = "manual_fan",
                binding = "settings.manual_fan",
            )
        }

        val settingsWidgets = core.settings
            .filter { it.mutableAtRuntime }
            .filterNot { it.id in setOf("mode", "manual_fan") }
            .mapNotNull(::settingWidget)

        val pages = mutableListOf(
            UiPage(
                id = "dashboard",
                title = "状態",
                widgets = dashboardWidgets,
            )
        )

        if (settingsWidgets.isNotEmpty()) {
            pages += UiPage(
                id = "settings",
                title = "設定",
                widgets = settingsWidgets,
            )
        }

        core.logging?.let { logging ->
            pages += UiPage(
                id = "history",
                title = "履歴",
                widgets = logging.channelIds.map { channel ->
                    UiWidget.LineChart(
                        id = "history_" + channel,
                        binding = "logging." + channel,
                    )
                },
            )
        }

        UiSpec(pages)
    }

    private fun settingWidget(setting: ProjectSetting): UiWidget? {
        return when (setting.type) {
            SettingType.NUMBER -> {
                val min = setting.constraints.min ?: return null
                val max = setting.constraints.max ?: return null
                UiWidget.Slider(
                    id = setting.id,
                    binding = "settings." + setting.id,
                    min = min,
                    max = max,
                    step = setting.constraints.step ?: 1.0,
                )
            }

            SettingType.BOOLEAN -> UiWidget.Toggle(
                id = setting.id,
                binding = "settings." + setting.id,
            )

            SettingType.ENUM -> UiWidget.Select(
                id = setting.id,
                binding = "settings." + setting.id,
                options = setting.constraints.allowedValues,
            )

            else -> null
        }
    }
}
