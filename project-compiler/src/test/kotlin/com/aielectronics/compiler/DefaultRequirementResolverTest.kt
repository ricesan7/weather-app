package com.aielectronics.compiler

import com.aielectronics.core.model.IntentDraft
import com.aielectronics.core.model.RequirementSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DefaultRequirementResolverTest {

    private val resolver = DefaultRequirementResolver()

    @Test
    fun `fan project receives safe technical defaults without asking GPIO-like questions`() {
        val result = resolver.resolve(
            IntentDraft(
                rawText = "温度が上がったらファンを回してスマホで履歴も見たい",
                interpretedGoal = "温度に応じてファンを自動制御し履歴を表示する",
                extractedFacts = mapOf(
                    "has_actuator" to "true",
                    "automation_required" to "true",
                    "automation_rule" to "temperature threshold",
                    "logging_requested" to "true",
                ),
            )
        )

        val ready = assertIs<RequirementResolution.Ready>(result)
        assertEquals("guided_solder", ready.requirements.slots.getValue("req_installation").value)
        assertEquals("ble_local", ready.requirements.slots.getValue("req_connectivity").value)
        assertEquals(
            RequirementSource.INFERRED_SAFE_DEFAULT,
            ready.requirements.slots.getValue("req_failure_behavior").source,
        )
        assertTrue(ready.requirements.unresolved.isEmpty())
    }

    @Test
    fun `hazardous environment wording asks only for physical environment detail`() {
        val result = resolver.resolve(
            IntentDraft(
                rawText = "屋外で使う温度監視装置",
                interpretedGoal = "屋外で温度を監視する",
            )
        )

        val need = assertIs<RequirementResolution.NeedUserInput>(result)
        assertEquals(listOf("req_environment"), need.missing.map { it.slotId })
        assertTrue(need.missing.single().userQuestion.contains("設置環境"))
    }

    @Test
    fun `missing goal produces one plain-language question`() {
        val result = resolver.resolve(IntentDraft(rawText = "", interpretedGoal = null))

        val need = assertIs<RequirementResolution.NeedUserInput>(result)
        assertEquals(1, need.missing.size)
        assertEquals("req_goal", need.missing.single().slotId)
    }
}
