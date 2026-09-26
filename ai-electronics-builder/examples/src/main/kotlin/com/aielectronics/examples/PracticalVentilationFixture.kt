package com.aielectronics.examples

import com.aielectronics.core.model.*

object PracticalVentilationFixture {
    fun design(): DesignIr = DesignIr(
        schemaVersion = "1.0",
        project = ProjectInfo(
            id = "reference_env_controller",
            name = "環境監視・自動換気コントローラー",
            goal = "温湿度を監視・記録し、条件に応じてファンを自動制御し、スマホから設定・手動操作・履歴確認を行う",
        ),
        capabilities = setOf(
            CapabilityId("multi_rule"),
            CapabilityId("persistent_settings"),
            CapabilityId("logging"),
            CapabilityId("generated_ui"),
            CapabilityId("manual_override"),
            CapabilityId("self_test"),
            CapabilityId("failsafe"),
        ),
        board = BoardSelection("xiao_esp32s3", setOf(TransportKind.BLE)),
        power = PowerPlan(
            domains = listOf(
                PowerDomain("logic_3v3", 3.3, 100.0, setOf("mcu", "sensor")),
                PowerDomain("load_5v", 5.0, 500.0, setOf("fan", "driver")),
            ),
            sources = listOf(PowerSource("external_5v", 5.0, 2000.0, Polarity.CENTER_POSITIVE)),
        ),
        components = listOf(
            ComponentInstance("mcu", "xiao_esp32s3", "controller"),
            ComponentInstance("sensor", "ae_sht31", "temperature_humidity"),
            ComponentInstance("driver", "tbd62003apg", "fan_driver"),
            ComponentInstance("fan", "fan_ydm2510c05", "ventilation"),
        ),
        connections = emptyList(),
        behavior = BehaviorGraph(
            rules = listOf(
                BehaviorRule(
                    id = "auto_on",
                    condition = Expression.Raw("mode == AUTO && (temperature >= temp_on || humidity >= rh_on)"),
                    actions = listOf(Action.SetOutput("fan", "ON")),
                ),
                BehaviorRule(
                    id = "auto_off",
                    condition = Expression.Raw("mode == AUTO && temperature <= temp_off && humidity <= rh_off"),
                    actions = listOf(Action.SetOutput("fan", "OFF")),
                ),
            ),
            failsafe = listOf(
                FailsafeSpec(
                    id = "sensor_timeout",
                    condition = Expression.Raw("sensor.invalid_for >= 5s"),
                    actions = listOf(Action.SetOutput("fan", "OFF"), Action.RaiseEvent("sensor_fault")),
                )
            ),
        ),
        settings = listOf(
            ProjectSetting("mode", SettingType.ENUM, "AUTO", true, SettingConstraints(allowedValues = listOf("AUTO", "MANUAL"))),
            ProjectSetting("temp_on", SettingType.NUMBER, "30.0", true, SettingConstraints(min = 20.0, max = 45.0, step = 0.5)),
            ProjectSetting("temp_off", SettingType.NUMBER, "28.0", true, SettingConstraints(min = 15.0, max = 44.0, step = 0.5, relationalRule = "temp_off < temp_on")),
        ),
        logging = LoggingSpec(listOf("temperature", "humidity", "fan_state"), 60, 7, StorageTarget.PHONE),
        ui = UiSpec(
            pages = listOf(
                UiPage("dashboard", "状態", listOf(
                    UiWidget.ValueCard("temp", "telemetry.temperature", "°C"),
                    UiWidget.ValueCard("rh", "telemetry.humidity", "%"),
                    UiWidget.Status("fan_state", "telemetry.fan_state"),
                    UiWidget.Select("mode", "settings.mode", listOf("AUTO", "MANUAL")),
                    UiWidget.Toggle("fan_manual", "settings.manual_fan"),
                )),
                UiPage("history", "履歴", listOf(UiWidget.LineChart("history_temp", "logging.temperature"))),
            )
        ),
        tests = listOf(
            TestSpec("sht31_probe", "温湿度センサー確認", "probe_i2c_0x45", true),
            TestSpec("fan_test", "ファン確認", "fan_on_1s_then_off", true),
        ),
        diagnostics = emptyList(),
        assembly = AssemblySpec(AssemblyMode.GUIDED_SOLDER, emptyList()),
        deployment = DeploymentSpec("fw_beginner_runtime", "1.0"),
        safety = SafetySummary(ValidationState.PASS, emptyList()),
    )
}
