package com.aielectronics.compiler

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RuleBasedAutoDecisionEngineTest {

    private val engine = RuleBasedAutoDecisionEngine()

    @Test
    fun `beginner receives verified automatic board decision`() {
        val result = engine.decide(
            AutoDecisionContext(
                decisionId = "auto_board",
                facts = mapOf("recommended.auto_board" to "xiao_esp32s3"),
                advancedMode = false,
            ),
            DecisionPolicy(
                decisionId = "auto_board",
                beginnerPolicy = BeginnerDecisionPolicy.AUTO,
                askOnlyWhen = null,
                userQuestion = null,
                advancedOverrideAllowed = true,
            )
        )

        val decided = assertIs<DecisionResult.Decided>(result)
        assertEquals("xiao_esp32s3", decided.value)
    }

    @Test
    fun `missing technical candidate blocks instead of asking beginner to choose`() {
        val result = engine.decide(
            AutoDecisionContext(
                decisionId = "auto_pin",
                facts = emptyMap(),
                advancedMode = false,
            ),
            DecisionPolicy(
                decisionId = "auto_pin",
                beginnerPolicy = BeginnerDecisionPolicy.AUTO_SAFE,
                askOnlyWhen = null,
                userQuestion = "GPIOを選んでください",
                advancedOverrideAllowed = true,
            )
        )

        val blocked = assertIs<DecisionResult.Blocked>(result)
        assertTrue(blocked.reason.contains("Do not expose"))
    }

    @Test
    fun `advanced override is honored when explicitly allowed`() {
        val result = engine.decide(
            AutoDecisionContext(
                decisionId = "auto_board",
                facts = mapOf(
                    "recommended.auto_board" to "xiao_esp32s3",
                    "override.auto_board" to "esp32_s3_devkitc1_n8r8",
                ),
                advancedMode = true,
            ),
            DecisionPolicy(
                decisionId = "auto_board",
                beginnerPolicy = BeginnerDecisionPolicy.AUTO,
                askOnlyWhen = null,
                userQuestion = null,
                advancedOverrideAllowed = true,
            )
        )

        assertEquals(
            "esp32_s3_devkitc1_n8r8",
            assertIs<DecisionResult.Decided>(result).value,
        )
    }
}
