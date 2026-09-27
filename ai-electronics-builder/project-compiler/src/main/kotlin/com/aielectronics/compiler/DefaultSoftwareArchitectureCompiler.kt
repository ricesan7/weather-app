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

        val appChannels = integrationChannels(bridge, ui)

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
                    channels = appChannels,
                    pages = integrationPages(ui, appChannels),
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
        ui: UiSpec,
    ): List<AppBridgeChannel> = buildList {
        bridge.telemetry.forEach { telemetry ->
            val widget = preferredWidget(ui, telemetry.binding, command = false)
            add(
                AppBridgeChannel(
                    id = telemetry.id,
                    binding = telemetry.binding,
                    direction = AppBridgeDirection.HARDWARE_TO_BASE44,
                    valueType = telemetry.valueType,
                    displayName = widgetDisplayName(widget, telemetry.id),
                    presentation = widgetPresentation(
                        widget,
                        fallback = AppBridgePresentation.VALUE,
                    ),
                    unit = telemetry.unit,
                    min = (widget as? UiWidget.Gauge)?.min,
                    max = (widget as? UiWidget.Gauge)?.max,
                )
            )
        }

        bridge.commands.forEach { command ->
            val widget = preferredWidget(ui, command.binding, command = true)
            add(
                AppBridgeChannel(
                    id = command.id,
                    binding = command.binding,
                    direction = AppBridgeDirection.BASE44_TO_HARDWARE,
                    valueType = command.valueType,
                    displayName = widgetDisplayName(widget, command.id),
                    presentation = widgetPresentation(
                        widget,
                        fallback = when (command.valueType) {
                            BridgeValueType.BOOLEAN -> AppBridgePresentation.TOGGLE
                            BridgeValueType.NUMBER -> AppBridgePresentation.SLIDER
                            BridgeValueType.ENUM -> AppBridgePresentation.SELECT
                            BridgeValueType.ACTION -> AppBridgePresentation.BUTTON
                            else -> AppBridgePresentation.VALUE
                        },
                    ),
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
                    displayName = humanize(event.id),
                    presentation = AppBridgePresentation.EVENT,
                )
            )
        }
    }

    private fun integrationPages(
        ui: UiSpec,
        channels: List<AppBridgeChannel>,
    ): List<AppBridgePage> {
        val channelBindings = channels.associateBy { it.binding }

        return ui.pages.mapIndexed { pageIndex, page ->
            AppBridgePage(
                id = page.id,
                title = page.title,
                order = pageIndex,
                widgets = page.widgets.mapIndexedNotNull { widgetIndex, widget ->
                    val binding = appBinding(widget)
                    val channel = channelBindings[binding]

                    if (channel == null && widget !is UiWidget.LineChart) {
                        return@mapIndexedNotNull null
                    }

                    AppBridgeWidget(
                        id = widget.id,
                        binding = binding,
                        displayName = widgetDisplayName(widget, widget.id),
                        presentation = pagePresentation(widget),
                        span = widgetSpan(widget),
                        order = widgetIndex,
                    )
                },
            )
        }.filter { it.widgets.isNotEmpty() }
    }

    private fun appBinding(widget: UiWidget): String = when (widget) {
        is UiWidget.LineChart ->
            if (widget.binding.startsWith("logging.")) {
                "telemetry." + widget.binding.removePrefix("logging.")
            } else {
                widget.binding
            }
        else -> widget.binding
    }

    private fun pagePresentation(widget: UiWidget): AppBridgePresentation = when (widget) {
        is UiWidget.ValueCard -> AppBridgePresentation.VALUE
        is UiWidget.Gauge -> AppBridgePresentation.GAUGE
        is UiWidget.LineChart -> AppBridgePresentation.LINE_CHART
        is UiWidget.Toggle -> AppBridgePresentation.TOGGLE
        is UiWidget.Slider -> AppBridgePresentation.SLIDER
        is UiWidget.Select -> AppBridgePresentation.SELECT
        is UiWidget.Button -> AppBridgePresentation.BUTTON
        is UiWidget.Status -> AppBridgePresentation.STATUS
        is UiWidget.Alarm -> AppBridgePresentation.ALARM
    }

    private fun widgetSpan(widget: UiWidget): AppBridgeWidgetSpan = when (widget) {
        is UiWidget.LineChart,
        is UiWidget.Alarm -> AppBridgeWidgetSpan.FULL
        is UiWidget.ValueCard,
        is UiWidget.Gauge,
        is UiWidget.Status -> AppBridgeWidgetSpan.THIRD
        else -> AppBridgeWidgetSpan.HALF
    }

    private fun preferredWidget(
        ui: UiSpec,
        binding: String,
        command: Boolean,
    ): UiWidget? {
        val matches = ui.pages
            .flatMap { it.widgets }
            .filter { it.binding == binding }

        if (command) return matches.firstOrNull()

        return matches.minByOrNull { widget ->
            when (widget) {
                is UiWidget.Gauge -> 0
                is UiWidget.ValueCard -> 1
                is UiWidget.Status -> 2
                is UiWidget.Alarm -> 3
                is UiWidget.LineChart -> 4
                else -> 5
            }
        }
    }

    private fun widgetDisplayName(
        widget: UiWidget?,
        fallbackId: String,
    ): String = when (widget) {
        is UiWidget.Button -> widget.label
        null -> humanize(fallbackId)
        else -> humanize(widget.id)
    }

    private fun widgetPresentation(
        widget: UiWidget?,
        fallback: AppBridgePresentation,
    ): AppBridgePresentation = when (widget) {
        is UiWidget.ValueCard -> AppBridgePresentation.VALUE
        is UiWidget.Gauge -> AppBridgePresentation.GAUGE
        is UiWidget.Status -> AppBridgePresentation.STATUS
        is UiWidget.Alarm -> AppBridgePresentation.ALARM
        is UiWidget.Toggle -> AppBridgePresentation.TOGGLE
        is UiWidget.Slider -> AppBridgePresentation.SLIDER
        is UiWidget.Select -> AppBridgePresentation.SELECT
        is UiWidget.Button -> AppBridgePresentation.BUTTON
        is UiWidget.LineChart -> AppBridgePresentation.LINE_CHART
        null -> fallback
    }

    private fun humanize(value: String): String =
        value
            .replace('_', ' ')
            .replace('-', ' ')
            .trim()

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
