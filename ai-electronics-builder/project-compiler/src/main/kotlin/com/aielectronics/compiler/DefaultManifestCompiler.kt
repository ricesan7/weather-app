package com.aielectronics.compiler

import com.aielectronics.core.model.*
import com.aielectronics.parts.EngineeringCatalog

class DefaultManifestCompiler(
    private val catalog: EngineeringCatalog,
    private val minimumRuntimeVersion: String = "0.1.0",
) : ManifestCompiler {

    override fun compile(core: DesignCore): Result<ProjectManifest> = runCatching {
        val drivers = linkedSetOf<String>()
        val devices = mutableListOf<ManifestDevice>()

        core.components.forEach { instance ->
            val spec = catalog.component(instance.componentId)
                ?: error("Component spec missing: " + instance.componentId)

            val driverId = spec.driverId ?: when (spec.kind) {
                ComponentKind.ACTUATOR -> "drv_binary_output"
                ComponentKind.DRIVER -> "drv_gpio_sink"
                else -> null
            }

            if (driverId != null) {
                drivers += driverId
                val config = linkedMapOf<String, String>()

                spec.i2cAddress?.let { config["address"] = it }

                core.connections
                    .filter {
                        (it.from.entityId == instance.instanceId ||
                            it.to.entityId == instance.instanceId)
                    }
                    .forEach { connection ->
                        val boardEndpoint = when {
                            connection.from.entityId == core.board.boardId -> connection.from
                            connection.to.entityId == core.board.boardId -> connection.to
                            else -> null
                        }
                        if (boardEndpoint != null) {
                            config["board_pin"] = boardEndpoint.pinId
                        }
                    }

                devices += ManifestDevice(
                    instanceId = instance.instanceId,
                    driverId = driverId,
                    config = config,
                )
            }
        }

        val buses = compileBuses(core)
        val gpio = compileGpio(core)
        val tests = compileTests(core)
        val telemetry = compileTelemetry(core)

        ProjectManifest(
            version = core.deployment.manifestVersion,
            projectId = core.project.id,
            boardId = core.board.boardId,
            drivers = drivers.toList(),
            buses = buses,
            gpio = gpio,
            devices = devices,
            rules = core.behavior.rules,
            settings = core.settings,
            interlocks = core.behavior.interlocks,
            failsafe = core.behavior.failsafe,
            telemetryIds = telemetry,
            tests = tests,
            minimumRuntimeVersion = minimumRuntimeVersion,
            autonomy = core.autonomy,
        )
    }

    private fun compileBuses(core: DesignCore): List<ManifestBus> {
        val sda = boardPinFor(core, NetType.I2C_SDA)
        val scl = boardPinFor(core, NetType.I2C_SCL)

        return if (sda != null && scl != null) {
            listOf(
                ManifestBus(
                    id = "i2c0",
                    kind = "I2C",
                    pins = mapOf("SDA" to sda, "SCL" to scl),
                )
            )
        } else {
            emptyList()
        }
    }

    private fun compileGpio(core: DesignCore): List<ManifestGpio> =
        core.connections
            .filter { it.netType == NetType.CONTROL }
            .mapNotNull { connection ->
                val boardEndpoint = when {
                    connection.from.entityId == core.board.boardId -> connection.from
                    connection.to.entityId == core.board.boardId -> connection.to
                    else -> null
                }

                boardEndpoint?.let {
                    ManifestGpio(
                        pinId = it.pinId,
                        mode = "OUTPUT",
                        safeValue = "LOW",
                    )
                }
            }
            .distinctBy { it.pinId }

    private fun compileTests(core: DesignCore): List<TestSpec> {
        val tests = mutableListOf<TestSpec>()

        if (core.capabilities.any { it.value == "measure_temperature" || it.value == "measure_humidity" }) {
            tests += TestSpec(
                id = "sensor_probe",
                name = "センサー確認",
                command = "probe_required_sensors",
                required = true,
            )
        }

        if (core.capabilities.any { it.value == "actuate_fan" }) {
            tests += TestSpec(
                id = "fan_output_test",
                name = "ファン確認",
                command = "fan_on_1s_then_off",
                required = true,
            )
        }

        return tests
    }

    private fun compileTelemetry(core: DesignCore): List<String> = buildList {
        if (core.capabilities.any { it.value == "measure_temperature" }) add("temperature")
        if (core.capabilities.any { it.value == "measure_humidity" }) add("humidity")
        if (core.capabilities.any { it.value == "actuate_fan" }) add("fan_state")
    }

    private fun boardPinFor(core: DesignCore, type: NetType): String? =
        core.connections.firstOrNull { it.netType == type }?.let { connection ->
            when {
                connection.from.entityId == core.board.boardId -> connection.from.pinId
                connection.to.entityId == core.board.boardId -> connection.to.pinId
                else -> null
            }
        }
}
