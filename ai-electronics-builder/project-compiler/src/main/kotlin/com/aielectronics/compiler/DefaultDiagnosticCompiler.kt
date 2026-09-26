package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultDiagnosticCompiler : DiagnosticCompiler {

    override fun compile(core: DesignCore): Result<DiagnosticBundle> = runCatching {
        val tests = mutableListOf<TestSpec>()
        val diagnostics = mutableListOf<DiagnosticSpec>()
        val capabilities = core.capabilities.map { it.value }.toSet()

        if (capabilities.any { it.startsWith("measure_") || it.startsWith("sense_") }) {
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
                    "センサーの電源線",
                    "センサーのGND",
                    "SDA/SCLの配線",
                    "I2Cアドレス設定",
                ),
                buildStepIds = sensorConnectionIds(core)
                    .map { "connection:" + it },
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
                    "ファンの極性",
                ),
                buildStepIds = fanConnectionIds(core)
                    .map { "connection:" + it },
            )
        }

        DiagnosticBundle(
            testPlan = TestPlan(tests),
            diagnostics = diagnostics,
        )
    }

    private fun sensorConnectionIds(core: DesignCore): List<String> {
        val sensorIds = core.components
            .filter { instance ->
                instance.role.contains("sensor", ignoreCase = true) ||
                    instance.role.contains("temperature", ignoreCase = true) ||
                    instance.role.contains("humidity", ignoreCase = true) ||
                    instance.componentId.contains("sht", ignoreCase = true) ||
                    instance.componentId.contains("bme", ignoreCase = true)
            }
            .map { it.instanceId }
            .toSet()

        return core.connections
            .filter { connection ->
                connection.from.entityId in sensorIds ||
                    connection.to.entityId in sensorIds ||
                    connection.netType == NetType.I2C_SDA ||
                    connection.netType == NetType.I2C_SCL
            }
            .map { it.id }
            .distinct()
    }

    private fun fanConnectionIds(core: DesignCore): List<String> {
        val fanIds = core.components
            .filter { instance ->
                instance.role.contains("fan", ignoreCase = true) ||
                    instance.role.contains("ventilation", ignoreCase = true) ||
                    instance.componentId.contains("fan", ignoreCase = true) ||
                    instance.componentId.contains("ydm", ignoreCase = true)
            }
            .map { it.instanceId }
            .toSet()

        val driverIds = core.components
            .filter { instance ->
                instance.role.contains("driver", ignoreCase = true) ||
                    instance.componentId.contains("tbd", ignoreCase = true)
            }
            .map { it.instanceId }
            .toSet()

        val powerSourceIds = core.power.sources.map { it.id }.toSet()
        val relatedIds = fanIds + driverIds

        return core.connections
            .filter { connection ->
                connection.from.entityId in relatedIds ||
                    connection.to.entityId in relatedIds ||
                    (
                        connection.netType == NetType.GROUND &&
                            (
                                connection.from.entityId in powerSourceIds ||
                                    connection.to.entityId in powerSourceIds
                            ) &&
                            (
                                connection.from.entityId == core.board.boardId ||
                                    connection.to.entityId == core.board.boardId
                            )
                    )
            }
            .map { it.id }
            .distinct()
    }
}
