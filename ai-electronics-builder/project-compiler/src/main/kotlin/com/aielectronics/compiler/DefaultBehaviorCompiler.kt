package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultBehaviorCompiler : BehaviorCompiler {

    override fun compile(
        requirements: ResolvedRequirements,
        capabilities: CapabilitySet,
    ): Result<BehaviorCompilation> = runCatching {
        val ids = capabilities.values.map { it.value }.toSet()
        val hasFan = "actuate_fan" in ids
        val hasTemperature = "measure_temperature" in ids
        val hasHumidity = "measure_humidity" in ids
        val loggingEnabled = "logging" in ids
        val manualOverride = "manual_override" in ids

        val settings = mutableListOf<ProjectSetting>()
        val rules = mutableListOf<BehaviorRule>()
        val failsafe = mutableListOf<FailsafeSpec>()
        val events = mutableListOf<EventSpec>()

        if (hasFan) {
            settings += ProjectSetting(
                id = "mode",
                type = SettingType.ENUM,
                defaultValue = "AUTO",
                mutableAtRuntime = true,
                constraints = SettingConstraints(
                    allowedValues = if (manualOverride) listOf("AUTO", "MANUAL") else listOf("AUTO")
                ),
            )
        }

        if (hasTemperature && hasFan) {
            val tempOn = value(requirements, "temp_on", "30.0")
            val tempOff = value(requirements, "temp_off", "28.0")

            require(tempOff.toDouble() < tempOn.toDouble()) {
                "temp_off must be lower than temp_on"
            }

            settings += ProjectSetting(
                id = "temp_on",
                type = SettingType.NUMBER,
                defaultValue = tempOn,
                mutableAtRuntime = true,
                constraints = SettingConstraints(
                    min = 0.0,
                    max = 60.0,
                    step = 0.5,
                ),
            )
            settings += ProjectSetting(
                id = "temp_off",
                type = SettingType.NUMBER,
                defaultValue = tempOff,
                mutableAtRuntime = true,
                constraints = SettingConstraints(
                    min = 0.0,
                    max = 59.5,
                    step = 0.5,
                    relationalRule = "temp_off < temp_on",
                ),
            )
        }

        if (hasHumidity && hasFan) {
            val rhOn = value(requirements, "rh_on", "75.0")
            val rhOff = value(requirements, "rh_off", "70.0")

            require(rhOff.toDouble() < rhOn.toDouble()) {
                "rh_off must be lower than rh_on"
            }

            settings += ProjectSetting(
                id = "rh_on",
                type = SettingType.NUMBER,
                defaultValue = rhOn,
                mutableAtRuntime = true,
                constraints = SettingConstraints(
                    min = 0.0,
                    max = 100.0,
                    step = 1.0,
                ),
            )
            settings += ProjectSetting(
                id = "rh_off",
                type = SettingType.NUMBER,
                defaultValue = rhOff,
                mutableAtRuntime = true,
                constraints = SettingConstraints(
                    min = 0.0,
                    max = 99.0,
                    step = 1.0,
                    relationalRule = "rh_off < rh_on",
                ),
            )
        }

        if (manualOverride && hasFan) {
            settings += ProjectSetting(
                id = "manual_fan",
                type = SettingType.BOOLEAN,
                defaultValue = "false",
                mutableAtRuntime = true,
            )

            rules += BehaviorRule(
                id = "manual_fan_on",
                condition = Expression.Raw("mode == MANUAL && manual_fan == true"),
                actions = listOf(Action.SetOutput("fan", "ON")),
                priority = 100,
            )
            rules += BehaviorRule(
                id = "manual_fan_off",
                condition = Expression.Raw("mode == MANUAL && manual_fan == false"),
                actions = listOf(Action.SetOutput("fan", "OFF")),
                priority = 100,
            )
        }

        if (hasFan && (hasTemperature || hasHumidity)) {
            val onTerms = mutableListOf<String>()
            val offTerms = mutableListOf<String>()

            if (hasTemperature) {
                onTerms += "temperature >= temp_on"
                offTerms += "temperature <= temp_off"
            }
            if (hasHumidity) {
                onTerms += "humidity >= rh_on"
                offTerms += "humidity <= rh_off"
            }

            rules += BehaviorRule(
                id = "auto_fan_on",
                condition = Expression.Raw(
                    "mode == AUTO && (" + onTerms.joinToString(" || ") + ")"
                ),
                actions = listOf(Action.SetOutput("fan", "ON")),
                priority = 50,
            )
            rules += BehaviorRule(
                id = "auto_fan_off",
                condition = Expression.Raw(
                    "mode == AUTO && " + offTerms.joinToString(" && ")
                ),
                actions = listOf(Action.SetOutput("fan", "OFF")),
                priority = 50,
            )

            failsafe += FailsafeSpec(
                id = "sensor_timeout",
                condition = Expression.Raw("required_sensor_invalid_for >= 5s"),
                actions = listOf(
                    Action.SetOutput("fan", "OFF"),
                    Action.RaiseEvent("sensor_fault"),
                ),
            )

            events += EventSpec(
                id = "sensor_fault",
                condition = Expression.Raw("required_sensor_invalid_for >= 5s"),
                severity = Severity.CRITICAL,
            )
        }

        val logging = if (loggingEnabled) {
            val channels = buildList {
                if (hasTemperature) add("temperature")
                if (hasHumidity) add("humidity")
                if (hasFan) add("fan_state")
            }
            LoggingSpec(
                channelIds = channels,
                intervalSeconds = value(requirements, "logging_interval_seconds", "60").toInt(),
                retentionDays = value(requirements, "logging_retention_days", "7").toInt(),
                primaryStorage = StorageTarget.PHONE,
            )
        } else {
            null
        }

        BehaviorCompilation(
            graph = BehaviorGraph(
                rules = rules,
                interlocks = emptyList(),
                failsafe = failsafe,
            ),
            settings = settings,
            logging = logging,
            events = events,
        )
    }

    private fun value(
        requirements: ResolvedRequirements,
        id: String,
        fallback: String,
    ): String = requirements.slots[id]?.value ?: fallback
}
