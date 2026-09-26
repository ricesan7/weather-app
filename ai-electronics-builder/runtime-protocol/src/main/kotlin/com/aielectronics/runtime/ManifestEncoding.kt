package com.aielectronics.runtime

import com.aielectronics.core.model.ProjectManifest

interface ProjectManifestEncoder {
    fun encode(manifest: ProjectManifest): String
}

class CanonicalManifestEncoder : ProjectManifestEncoder {

    override fun encode(manifest: ProjectManifest): String {
        val lines = mutableListOf<String>()
        lines += "version=" + manifest.version
        lines += "project=" + manifest.projectId
        lines += "board=" + manifest.boardId
        lines += "runtime_min=" + manifest.minimumRuntimeVersion

        manifest.drivers.sorted().forEach { driver ->
            lines += "driver=" + driver
        }

        manifest.buses.sortedBy { it.id }.forEach { bus ->
            val pins = bus.pins.toSortedMap()
                .entries
                .joinToString(",") { entry -> entry.key + ":" + entry.value }
            lines += "bus=" + bus.id + "," + bus.kind + "," + pins
        }

        manifest.gpio.sortedBy { it.pinId }.forEach { gpio ->
            lines += "gpio=" + gpio.pinId + "," + gpio.mode + "," + (gpio.safeValue ?: "")
        }

        manifest.devices.sortedBy { it.instanceId }.forEach { device ->
            val config = device.config.toSortedMap()
                .entries
                .joinToString(",") { entry -> entry.key + ":" + entry.value }
            lines += "device=" + device.instanceId + "," + device.driverId + "," + config
        }

        manifest.settings.sortedBy { it.id }.forEach { setting ->
            lines += "setting=" + setting.id + "," + setting.type.name + "," + setting.defaultValue
        }

        manifest.telemetryIds.sorted().forEach { telemetry ->
            lines += "telemetry=" + telemetry
        }

        manifest.tests.sortedBy { it.id }.forEach { test ->
            lines += "test=" + test.id + "," + test.command + "," + test.required
        }

        return lines.joinToString("\n")
    }
}
