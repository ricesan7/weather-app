package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultSoftwareArchitectureCompiler : SoftwareArchitectureCompiler {

    override fun compile(
        requirements: ResolvedRequirements,
        core: DesignCore,
        ui: UiSpec,
    ): Result<SoftwarePlan> = runCatching {
        val goal = requirements.goal.lowercase()
        val reasons = mutableListOf<String>()

        val explicitMobileRequest = goal.containsAny(
            "スマホ",
            "スマートフォン",
            "android",
            "mobile app",
            "phone app",
            "アプリ",
        )
        if (explicitMobileRequest) {
            reasons += "スマートフォン用の操作・表示ソフトが要求されています。"
        }

        val remoteOrNotificationRequest = goal.containsAny(
            "遠隔操作",
            "リモート操作",
            "外出先",
            "通知",
            "アラート",
            "警告",
            "push notification",
            "remote control",
            "alert",
        )
        if (remoteOrNotificationRequest) {
            reasons += "遠隔操作または通知を伴うソフトウェア機能が要求されています。"
        }

        val phoneLogging = core.logging?.primaryStorage == StorageTarget.PHONE
        if (phoneLogging) {
            reasons += "履歴データの保存先としてスマートフォンが必要です。"
        }

        val companionRequired =
            explicitMobileRequest || remoteOrNotificationRequest || phoneLogging

        if (!companionRequired) {
            return@runCatching SoftwarePlan()
        }

        val bridge = DeviceBridgeSpec(
            transport = selectTransport(core.board),
            commands = commandSpecs(ui),
            telemetry = telemetrySpecs(core, ui),
            events = core.events.map {
                DeviceBridgeEvent(
                    id = it.id,
                    severity = it.severity,
                )
            },
        )

        val alerts = alertSpecs(requirements, goal, bridge.telemetry)
        val applicationFeatures = buildList {
            if (bridge.telemetry.isNotEmpty()) add("live_telemetry")
            if (phoneLogging || core.logging != null) add("telemetry_history")
            if (bridge.commands.isNotEmpty()) add("device_settings")
            if (alerts.isNotEmpty()) add("configurable_alerts")
            if (alerts.any { it.notificationRequired }) add("notifications")
        }

        SoftwarePlan(
            companionSoftwareRequired = true,
            base44DesignRequired = true,
            hardwareBridgeRequired = true,
            reasons = reasons.distinct(),
            deviceBridge = bridge,
            base44Handoff = Base44HandoffSpec(
                projectId = core.project.id,
                projectName = core.project.name,
                goal = core.project.goal,
                uiSpec = ui,
                bridge = bridge,
                liveTelemetry = bridge.telemetry.map {
                    Base44TelemetryBinding(
                        sourceBinding = it.binding,
                        displayLabel = displayLabel(it.id),
                        unit = it.unit,
                        historyEnabled =
                            core.logging?.channelIds?.contains(it.id) == true,
                    )
                },
                alerts = alerts,
                applicationFeatures = applicationFeatures,
            ),
        )
    }

    private fun selectTransport(board: BoardSelection): BridgeTransport = when {
        TransportKind.BLE in board.transports -> BridgeTransport.BLE
        TransportKind.USB in board.transports -> BridgeTransport.USB
        TransportKind.WIFI in board.transports -> BridgeTransport.WIFI
        else -> error("Companion software requires a supported device transport")
    }

    private fun commandSpecs(ui: UiSpec): List<DeviceBridgeCommand> =
        ui.pages
            .flatMap { it.widgets }
            .mapNotNull { widget ->
                if (!widget.binding.startsWith("settings.")) {
                    return@mapNotNull null
                }

                when (widget) {
                    is UiWidget.Toggle -> DeviceBridgeCommand(
                        id = widget.id,
                        binding = widget.binding,
                        valueType = BridgeValueType.BOOLEAN,
                    )

                    is UiWidget.Slider -> DeviceBridgeCommand(
                        id = widget.id,
                        binding = widget.binding,
                        valueType = BridgeValueType.NUMBER,
                        min = widget.min,
                        max = widget.max,
                        step = widget.step,
                    )

                    is UiWidget.Select -> DeviceBridgeCommand(
                        id = widget.id,
                        binding = widget.binding,
                        valueType = BridgeValueType.ENUM,
                        allowedValues = widget.options,
                    )

                    is UiWidget.Button -> DeviceBridgeCommand(
                        id = widget.id,
                        binding = widget.binding,
                        valueType = BridgeValueType.ACTION,
                    )

                    else -> null
                }
            }
            .distinctBy { it.binding }

    private fun telemetrySpecs(
        core: DesignCore,
        ui: UiSpec,
    ): List<DeviceBridgeTelemetry> {
        val uiTelemetry = ui.pages
            .flatMap { it.widgets }
            .mapNotNull { widget ->
                if (!widget.binding.startsWith("telemetry.")) {
                    return@mapNotNull null
                }

                DeviceBridgeTelemetry(
                    id = widget.binding.removePrefix("telemetry."),
                    binding = widget.binding,
                    valueType = telemetryType(widget.binding),
                    unit = (widget as? UiWidget.ValueCard)?.unit,
                )
            }

        val loggedTelemetry = core.logging
            ?.channelIds
            .orEmpty()
            .map { channelId ->
                DeviceBridgeTelemetry(
                    id = channelId,
                    binding = "telemetry.$channelId",
                    valueType = telemetryType(channelId),
                    unit = unitFor(channelId),
                )
            }

        return (uiTelemetry + loggedTelemetry)
            .distinctBy { it.binding }
    }

    private fun alertSpecs(
        requirements: ResolvedRequirements,
        goal: String,
        telemetry: List<DeviceBridgeTelemetry>,
    ): List<Base44AlertSpec> {
        val wantsAlert = goal.containsAny(
            "通知",
            "アラート",
            "警告",
            "push notification",
            "alert",
        )
        if (!wantsAlert) return emptyList()

        val available = telemetry.associateBy { it.id }
        val alerts = mutableListOf<Base44AlertSpec>()

        if ("temperature" in available && goal.containsAny("温度", "temperature")) {
            alerts += Base44AlertSpec(
                id = "temperature_high",
                label = "高温アラート",
                sourceBinding = "telemetry.temperature",
                operator = AlertOperator.ABOVE,
                thresholdBinding = "appSettings.temperature_high_threshold",
                defaultThreshold =
                    requirements.slots["temp_on"]?.value?.toDoubleOrNull() ?: 30.0,
                min = -20.0,
                max = 80.0,
                step = 0.5,
                unit = "°C",
            )
        }

        if ("humidity" in available && goal.containsAny("湿度", "humidity")) {
            alerts += Base44AlertSpec(
                id = "humidity_high",
                label = "高湿度アラート",
                sourceBinding = "telemetry.humidity",
                operator = AlertOperator.ABOVE,
                thresholdBinding = "appSettings.humidity_high_threshold",
                defaultThreshold =
                    requirements.slots["rh_on"]?.value?.toDoubleOrNull() ?: 75.0,
                min = 0.0,
                max = 100.0,
                step = 1.0,
                unit = "%",
            )
        }

        return alerts
    }

    private fun displayLabel(id: String): String = when (id) {
        "temperature" -> "現在温度"
        "humidity" -> "現在湿度"
        "fan_state" -> "ファン状態"
        else -> id
    }

    private fun unitFor(id: String): String? = when (id) {
        "temperature" -> "°C"
        "humidity" -> "%"
        else -> null
    }

    private fun telemetryType(binding: String): BridgeValueType = when {
        binding.containsAny("temperature", "humidity", "voltage", "current") ->
            BridgeValueType.NUMBER
        else -> BridgeValueType.TEXT
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { contains(it.lowercase()) }
}
