package com.aielectronics.compiler

/**
 * Beginner mode must not turn missing backend/DB knowledge into a technical question.
 *
 * Verified candidate values are supplied by the resolver layer via facts such as:
 * - decision.<decisionId>
 * - recommended.<decisionId>
 * - override.<decisionId> (advanced mode only)
 *
 * If an AUTO/AUTO_SAFE decision has no verified candidate, compilation is blocked
 * instead of asking the beginner to choose a GPIO, library, bus, MCU, etc.
 */
class RuleBasedAutoDecisionEngine : AutoDecisionEngine {

    override fun decide(
        context: AutoDecisionContext,
        policy: DecisionPolicy,
    ): DecisionResult {
        if (context.advancedMode && policy.advancedOverrideAllowed) {
            context.facts["override.${policy.decisionId}"]?.let {
                return DecisionResult.Decided(it, "advanced user override")
            }
        }

        val candidate =
            context.facts["decision.${policy.decisionId}"]
                ?: context.facts["recommended.${policy.decisionId}"]
                ?: context.facts["verified_candidate"]

        if (!candidate.isNullOrBlank()) {
            return DecisionResult.Decided(
                value = candidate,
                rationale = "resolved automatically from verified design data",
            )
        }

        return when (policy.beginnerPolicy) {
            BeginnerDecisionPolicy.USER_REQUIRED -> {
                val question = policy.userQuestion
                    ?: return DecisionResult.Blocked(
                        "Decision ${policy.decisionId} requires user input but has no user-facing question."
                    )
                DecisionResult.NeedUserInput(question = question, blocking = true)
            }

            BeginnerDecisionPolicy.AUTO,
            BeginnerDecisionPolicy.AUTO_SAFE,
            BeginnerDecisionPolicy.AUTO_PROPOSE,
            -> DecisionResult.Blocked(
                "No verified automatic candidate for ${policy.decisionId}. " +
                    "Do not expose this technical choice to beginner mode."
            )
        }
    }
}
