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
            "push notification",
            "remote control",
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
                )
            }

        return (uiTelemetry + loggedTelemetry)
            .distinctBy { it.binding }
    }

    private fun telemetryType(binding: String): BridgeValueType = when {
        binding.containsAny("temperature", "humidity", "voltage", "current") ->
            BridgeValueType.NUMBER
        else -> BridgeValueType.TEXT
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { contains(it.lowercase()) }
}
