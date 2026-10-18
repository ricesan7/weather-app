package com.aielectronics.core.model

data class ResolvedRequirements(
    val goal: String,
    val slots: Map<String, RequirementValue>,
    val assumptions: List<Assumption> = emptyList(),
    val unresolved: List<MissingRequirement> = emptyList(),
    val requestedComponents: List<RequestedComponent> = emptyList(),
)

data class RequirementValue(
    val value: String,
    val source: RequirementSource,
    val confidence: Double,
)

enum class RequirementSource { USER, INFERRED_SAFE_DEFAULT, DERIVED, EXISTING_PROJECT }

data class MissingRequirement(
    val slotId: String,
    val reason: String,
    val safetyCritical: Boolean,
    val blocking: Boolean,
    val userQuestion: String,
)

data class IntentDraft(
    val rawText: String,
    val interpretedGoal: String?,
    val extractedFacts: Map<String, String> = emptyMap(),
)
