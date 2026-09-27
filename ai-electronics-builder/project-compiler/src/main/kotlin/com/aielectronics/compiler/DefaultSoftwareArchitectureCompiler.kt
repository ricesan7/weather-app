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
            reasons += "スマートフォン用ソフトウェアが要求されています。"
        }

        val remoteRequest = goal.containsAny(
            "遠隔操作",
            "リモート操作",
            "外出先",
            "remote control",
            "remote access",
        )
        if (remoteRequest) {
            reasons += "遠隔利用を伴うアプリケーション機能が要求されています。"
        }

        val notificationRequest = goal.containsAny(
            "通知",
            "アラート",
            "警告",
            "push notification",
            "alert",
        )
        if (notificationRequest) {
            reasons += "通知を伴うアプリケーション機能が要求されています。"
        }

        val phoneLogging = core.logging?.primaryStorage == StorageTarget.PHONE
        if (phoneLogging) {
            reasons += "履歴データをスマートフォン側で扱う必要があります。"
        }

        val companionRequired =
            explicitMobileRequest || remoteRequest || notificationRequest || phoneLogging

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

        val capabilities = buildSet {
            if (bridge.telemetry.isNotEmpty()) {
                add(Base44ApplicationCapability.LIVE_DATA)
            }
            if (core.logging != null) {
                add(Base44ApplicationCapability.HISTORY)
            }
            if (bridge.commands.isNotEmpty()) {
                add(Base44ApplicationCapability.DEVICE_CONTROL)
                add(Base44ApplicationCapability.DEVICE_SETTINGS)
            }
            if (notificationRequest) {
                add(Base44ApplicationCapability.NOTIFICATIONS)
            }
            if (remoteRequest) {
                add(Base44ApplicationCapability.REMOTE_ACCESS)
            }
            if (goal.containsAny("自動化", "automation")) {
                add(Base44ApplicationCapability.AUTOMATION)
            }
            if (goal.containsAny("ai", "人工知能")) {
                add(Base44ApplicationCapability.AI_FEATURES)
            }
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
                integration = AppHardwareIntegrationContract(
                    channels = integrationChannels(bridge),
                ),
                requestedCapabilities = capabilities,
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

    private fun integrationChannels(
        bridge: DeviceBridgeSpec,
    ): List<AppBridgeChannel> = buildList {
        bridge.telemetry.forEach { telemetry ->
            add(
                AppBridgeChannel(
                    id = telemetry.id,
                    binding = telemetry.binding,
                    direction = AppBridgeDirection.HARDWARE_TO_BASE44,
                    valueType = telemetry.valueType,
                    unit = telemetry.unit,
                )
            )
        }

        bridge.commands.forEach { command ->
            add(
                AppBridgeChannel(
                    id = command.id,
                    binding = command.binding,
                    direction = AppBridgeDirection.BASE44_TO_HARDWARE,
                    valueType = command.valueType,
                    min = command.min,
                    max = command.max,
                    step = command.step,
                    allowedValues = command.allowedValues,
                )
            )
        }

        bridge.events.forEach { event ->
            add(
                AppBridgeChannel(
                    id = event.id,
                    binding = "events." + event.id,
                    direction = AppBridgeDirection.HARDWARE_EVENT_TO_BASE44,
                    valueType = BridgeValueType.TEXT,
                )
            )
        }
    }

    private fun telemetryType(binding: String): BridgeValueType = when {
        binding.containsAny(
            "temperature",
            "humidity",
            "voltage",
            "current",
            "pressure",
            "distance",
            "speed",
            "level",
            "value",
        ) -> BridgeValueType.NUMBER
        else -> BridgeValueType.TEXT
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { contains(it.lowercase()) }
}
