package com.aielectronics.compiler

data class AutoDecisionContext(
    val decisionId: String,
    val facts: Map<String, String>,
    val advancedMode: Boolean,
)

data class DecisionPolicy(
    val decisionId: String,
    val beginnerPolicy: BeginnerDecisionPolicy,
    val askOnlyWhen: String?,
    val userQuestion: String?,
    val advancedOverrideAllowed: Boolean,
)

enum class BeginnerDecisionPolicy { AUTO, AUTO_PROPOSE, AUTO_SAFE, USER_REQUIRED }

sealed interface DecisionResult {
    data class Decided(val value: String, val rationale: String) : DecisionResult
    data class NeedUserInput(val question: String, val blocking: Boolean) : DecisionResult
    data class Blocked(val reason: String) : DecisionResult
}

interface AutoDecisionEngine {
    fun decide(context: AutoDecisionContext, policy: DecisionPolicy): DecisionResult
}
