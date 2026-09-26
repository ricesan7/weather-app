package com.aielectronics.compiler

import com.aielectronics.core.model.*

class DefaultCapabilityMapper : CapabilityMapper {

    override fun map(requirements: ResolvedRequirements): CapabilitySet {
        val capabilities = linkedSetOf<CapabilityId>()
        val goal = requirements.goal.lowercase()

        requirements.slots["capabilities"]?.value
            ?.split(",")
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            ?.forEach { capabilities += CapabilityId(it) }

        if (goal.containsAny("温度", "温湿度", "temperature")) {
            capabilities += CapabilityId("measure_temperature")
        }
        if (goal.containsAny("湿度", "温湿度", "humidity")) {
            capabilities += CapabilityId("measure_humidity")
        }
        if (goal.containsAny("気圧", "大気圧", "pressure", "barometer")) {
            capabilities += CapabilityId("measure_pressure")
        }
        if (goal.containsAny("照度", "明るさ", "illuminance", "lux")) {
            capabilities += CapabilityId("measure_illuminance")
        }
        if (goal.containsAny("ファン", "換気", "fan", "ventilation")) {
            capabilities += CapabilityId("actuate_fan")
        }

        if (
            requirements.slots["automation_required"]?.value.asBoolean() ||
            goal.containsAny("自動", "になったら", "応じて", "automatic", "when ")
        ) {
            capabilities += CapabilityId("automation_rules")
        }

        if (
            requirements.slots["req_logging"]?.value == "enabled" ||
            requirements.slots["logging_requested"]?.value.asBoolean() ||
            goal.containsAny("履歴", "記録", "ログ", "グラフ", "保存", "history", "log")
        ) {
            capabilities += CapabilityId("logging")
        }

        if (requirements.slots["req_manual_override"]?.value?.startsWith("enabled") == true) {
            capabilities += CapabilityId("manual_override")
        }

        if (requirements.slots["req_persistence"]?.value == "enabled") {
            capabilities += CapabilityId("persistent_settings")
        }

        capabilities += CapabilityId("generated_ui")
        capabilities += CapabilityId("self_test")
        capabilities += CapabilityId("failsafe")

        return CapabilitySet(capabilities)
    }

    private fun String.containsAny(vararg terms: String): Boolean =
        terms.any { contains(it.lowercase()) }

    private fun String?.asBoolean(): Boolean =
        this?.trim()?.lowercase() in setOf("true", "yes", "1", "enabled", "required")
}
