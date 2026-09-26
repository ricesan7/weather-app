package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultDiagnosticCompiler : DiagnosticCompiler {

    override fun compile(core: DesignCore): Result<DiagnosticBundle> = runCatching {
        val tests = mutableListOf<TestSpec>()
        val diagnostics = mutableListOf<DiagnosticSpec>()
        val capabilities = core.capabilities.map { it.value }.toSet()

        if ("measure_temperature" in capabilities || "measure_humidity" in capabilities) {
            tests += TestSpec(
                id = "sensor_probe",
                name = "センサー確認",
                command = "probe_required_sensors",
                required = true,
            )
            diagnostics += DiagnosticSpec(
                id = "diag_sensor_missing",
                trigger = "sensor_probe_failed",
                likelyCauses = listOf(
                    "VDD/GNDの接続",
                    "SDA/SCLの接続",
                    "I2Cアドレス設定",
                ),
                buildStepIds = listOf(
                    "connection:sensor_power",
                    "connection:i2c",
                ),
            )
        }

        if ("actuate_fan" in capabilities) {
            tests += TestSpec(
                id = "fan_output_test",
                name = "ファン確認",
                command = "fan_on_1s_then_off",
                required = true,
            )
            diagnostics += DiagnosticSpec(
                id = "diag_fan_not_rotating",
                trigger = "fan_output_test_failed",
                likelyCauses = listOf(
                    "5V外部電源",
                    "共通GND",
                    "ドライバ入力/出力",
                    "ファン極性",
                ),
                buildStepIds = listOf(
                    "connection:fan_power",
                    "connection:driver",
                    "connection:common_ground",
                ),
            )
        }

        DiagnosticBundle(
            testPlan = TestPlan(tests),
            diagnostics = diagnostics,
        )
    }
}
