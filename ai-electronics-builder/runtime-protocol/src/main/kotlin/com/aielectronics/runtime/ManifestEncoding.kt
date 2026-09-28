package com.aielectronics.runtime

import com.aielectronics.core.model.*

interface ProjectManifestEncoder {
    fun encode(manifest: ProjectManifest): String
}

class CanonicalManifestEncoder : ProjectManifestEncoder {

    override fun encode(manifest: ProjectManifest): String {
        val lines = mutableListOf<String>()

        lines += line("meta", "version", manifest.version)
        lines += line("meta", "project", manifest.projectId)
        lines += line("meta", "board", manifest.boardId)
        lines += line("meta", "runtime_min", manifest.minimumRuntimeVersion)
        lines += line(
            "autonomy",
            manifest.autonomy.coreOperationMode.name,
            manifest.autonomy.localBehaviorExecutionRequired.toString(),
            manifest.autonomy.localSafetyExecutionRequired.toString(),
            manifest.autonomy.persistRuntimeSettings.toString(),
            manifest.autonomy.externalInputReason.orEmpty(),
        )

        manifest.drivers.sorted().forEach { driver ->
            lines += line("driver", driver)
        }

        manifest.driverProfiles
            .sortedBy { it.driverId }
            .forEach { profile ->
                val parameters =
                    profile.parameters.toSortedMap()
                        .entries
                        .joinToString(",") {
                            entry ->
                            entry.key + ":" + entry.value
                        }
                val telemetry =
                    profile.telemetry
                        .sortedBy { it.id }
                        .joinToString(";") { item ->
                            listOf(
                                item.id,
                                item.unit,
                                item.source,
                                item.scale.toString(),
                                item.offset.toString(),
                            ).joinToString(",")
                        }
                lines += line(
                    "driver_profile",
                    profile.driverId,
                    profile.family.name,
                    profile.interfaceType.name,
                    profile.sampleIntervalMs.toString(),
                    parameters,
                    telemetry,
                )
            }

        manifest.buses.sortedBy { it.id }.forEach { bus ->
            val pins = bus.pins.toSortedMap()
                .entries
                .joinToString(",") { entry -> entry.key + ":" + entry.value }
            lines += line("bus", bus.id, bus.kind, pins)
        }

        manifest.gpio.sortedBy { it.pinId }.forEach { gpio ->
            lines += line("gpio", gpio.pinId, gpio.mode, gpio.safeValue.orEmpty())
        }

        manifest.devices.sortedBy { it.instanceId }.forEach { device ->
            val config = device.config.toSortedMap()
                .entries
                .joinToString(",") { entry -> entry.key + ":" + entry.value }
            lines += line("device", device.instanceId, device.driverId, config)
        }

        manifest.settings.sortedBy { it.id }.forEach { setting ->
            lines += line(
                "setting",
                setting.id,
                setting.type.name,
                setting.defaultValue,
                setting.mutableAtRuntime.toString(),
                setting.constraints.min?.toString().orEmpty(),
                setting.constraints.max?.toString().orEmpty(),
                setting.constraints.step?.toString().orEmpty(),
                setting.constraints.allowedValues.joinToString(","),
                setting.constraints.relationalRule.orEmpty(),
            )
        }

        manifest.rules.sortedWith(
            compareByDescending<BehaviorRule> { it.priority }.thenBy { it.id }
        ).forEach { rule ->
            lines += line(
                "rule",
                rule.id,
                rule.priority.toString(),
                raw(rule.condition),
                actions(rule.actions),
            )
        }

        manifest.interlocks.sortedBy { it.id }.forEach { interlock ->
            lines += line(
                "interlock",
                interlock.id,
                interlock.mandatory.toString(),
                raw(interlock.condition),
                interlock.blockedActions.sorted().joinToString(","),
            )
        }

        manifest.failsafe.sortedBy { it.id }.forEach { failsafe ->
            lines += line(
                "failsafe",
                failsafe.id,
                raw(failsafe.condition),
                actions(failsafe.actions),
            )
        }

        manifest.telemetryIds.sorted().forEach { telemetry ->
            lines += line("telemetry", telemetry)
        }

        manifest.tests.sortedBy { it.id }.forEach { test ->
            lines += line(
                "test",
                test.id,
                test.command,
                test.required.toString(),
            )
        }

        return lines.joinToString("\n")
    }

    private fun raw(expression: Expression): String = when (expression) {
        is Expression.Raw -> expression.expression
    }

    private fun actions(actions: List<Action>): String =
        actions.joinToString(";") { action ->
            when (action) {
                is Action.SetOutput -> "set:" + action.outputId + ":" + action.value
                is Action.RaiseEvent -> "event:" + action.eventId
                is Action.SetState -> "state:" + action.stateId
            }
        }

    private fun line(vararg fields: String): String =
        fields.joinToString("\t") { field -> escapeField(field) }

    private fun escapeField(value: String): String =
        value
            .replace("%", "%25")
            .replace("\t", "%09")
            .replace("\n", "%0A")
            .replace("\r", "%0D")
}
