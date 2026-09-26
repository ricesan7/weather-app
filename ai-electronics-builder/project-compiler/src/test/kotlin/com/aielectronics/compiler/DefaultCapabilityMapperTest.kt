package com.aielectronics.compiler

import com.aielectronics.core.model.*
import kotlin.test.Test
import kotlin.test.assertTrue

class DefaultCapabilityMapperTest {

    @Test
    fun `practical ventilation request maps hardware and product capabilities`() {
        val requirements = ResolvedRequirements(
            goal = "温度と湿度を監視して暑くなったら換気ファンを回し、履歴をスマホで見たい",
            slots = mapOf(
                "automation_required" to RequirementValue("true", RequirementSource.USER, 1.0),
                "req_logging" to RequirementValue("enabled", RequirementSource.INFERRED_SAFE_DEFAULT, 0.9),
                "req_manual_override" to RequirementValue("enabled_with_safety_interlocks", RequirementSource.INFERRED_SAFE_DEFAULT, 0.9),
                "req_persistence" to RequirementValue("enabled", RequirementSource.INFERRED_SAFE_DEFAULT, 0.9),
            ),
        )

        val values = DefaultCapabilityMapper().map(requirements).values.map { it.value }.toSet()

        assertTrue("measure_temperature" in values)
        assertTrue("measure_humidity" in values)
        assertTrue("actuate_fan" in values)
        assertTrue("automation_rules" in values)
        assertTrue("logging" in values)
        assertTrue("generated_ui" in values)
        assertTrue("self_test" in values)
        assertTrue("failsafe" in values)
    }
}
