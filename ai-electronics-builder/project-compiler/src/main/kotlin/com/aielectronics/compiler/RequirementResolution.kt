package com.aielectronics.compiler

import com.aielectronics.core.model.*

enum class RequirementNecessity {
    REQUIRED,
    DEFAULTABLE,
    OPTIONAL,
}

data class RequirementContext(
    val goal: String,
    val slots: Map<String, RequirementValue>,
) {
    fun value(id: String): String? = slots[id]?.value

    fun isTrue(id: String): Boolean =
        value(id)?.trim()?.lowercase() in setOf("true", "yes", "1", "enabled", "required")

    fun goalContainsAny(vararg terms: String): Boolean {
        val normalized = goal.lowercase()
        return terms.any { normalized.contains(it.lowercase()) }
    }
}

data class RequirementSlotDefinition(
    val id: String,
    val necessity: RequirementNecessity,
    val safetyCritical: Boolean,
    val question: String,
    val appliesWhen: (RequirementContext) -> Boolean = { true },
    val safeDefault: ((RequirementContext) -> String?)? = null,
)

object DefaultRequirementCatalog {
    val definitions: List<RequirementSlotDefinition> = listOf(
        RequirementSlotDefinition(
            id = "req_power_source",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = true,
            question = "使える電源に制約はありますか？",
            appliesWhen = { it.hasActuator() },
            safeDefault = { "auto_select_verified_low_voltage_supply" },
        ),
        RequirementSlotDefinition(
            id = "req_load_rating",
            necessity = RequirementNecessity.REQUIRED,
            safetyCritical = true,
            question = "すでに使うモーターやファンが決まっている場合、その型番を教えてください。",
            appliesWhen = { it.isTrue("fixed_load_model") && it.value("load_model") == null },
        ),
        RequirementSlotDefinition(
            id = "req_environment",
            necessity = RequirementNecessity.REQUIRED,
            safetyCritical = true,
            question = "屋内・屋外、水濡れ、高温など、設置環境を教えてください。",
            appliesWhen = {
                it.value("environment") == null &&
                    it.goalContainsAny("屋外", "防水", "水", "雨", "高温", "outdoor", "wet", "water")
            },
        ),
        RequirementSlotDefinition(
            id = "req_environment",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "設置環境を教えてください。",
            appliesWhen = { it.value("environment") == null },
            safeDefault = { "indoor_dry" },
        ),
        RequirementSlotDefinition(
            id = "req_connectivity",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "外出先からも操作する必要がありますか？",
            safeDefault = { ctx ->
                if (ctx.goalContainsAny("外出先", "遠隔", "internet", "remote")) "wifi_remote"
                else "ble_local"
            },
        ),
        RequirementSlotDefinition(
            id = "req_offline",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = true,
            question = "ネットが切れても自動制御を続ける必要がありますか？",
            safeDefault = { "core_control_must_continue_locally" },
        ),
        RequirementSlotDefinition(
            id = "req_rules",
            necessity = RequirementNecessity.REQUIRED,
            safetyCritical = false,
            question = "どんな条件になったら、何を動かしたいですか？",
            appliesWhen = {
                it.isTrue("automation_required") &&
                    it.value("automation_rule") == null &&
                    it.value("req_rules") == null
            },
        ),
        RequirementSlotDefinition(
            id = "req_interlocks",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = true,
            question = "必ず停止させたい条件はありますか？",
            appliesWhen = { it.hasActuator() },
            safeDefault = { "derive_mandatory_interlocks" },
        ),
        RequirementSlotDefinition(
            id = "req_manual_override",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "スマホから手動操作もできるようにしますか？",
            appliesWhen = { it.hasActuator() },
            safeDefault = { "enabled_with_safety_interlocks" },
        ),
        RequirementSlotDefinition(
            id = "req_logging",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "履歴やグラフは必要ですか？",
            safeDefault = { ctx ->
                if (ctx.goalContainsAny("履歴", "記録", "ログ", "グラフ", "history", "log")) "enabled"
                else "disabled"
            },
        ),
        RequirementSlotDefinition(
            id = "req_retention",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "履歴はどのくらい残したいですか？",
            appliesWhen = { it.value("req_logging") == "enabled" || it.isTrue("logging_requested") },
            safeDefault = { "7d_phone" },
        ),
        RequirementSlotDefinition(
            id = "req_persistence",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "設定値は再起動後も保持する前提でよいですか？",
            safeDefault = { "enabled" },
        ),
        RequirementSlotDefinition(
            id = "req_installation",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "完成品としてはんだ付けしますか？",
            safeDefault = { "guided_solder" },
        ),
        RequirementSlotDefinition(
            id = "req_failure_behavior",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = true,
            question = "センサー異常時は安全停止で問題ありませんか？",
            appliesWhen = { it.hasActuator() },
            safeDefault = { "safe_off_unless_domain_rule_requires_otherwise" },
        ),
        RequirementSlotDefinition(
            id = "req_runtime_duration",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = true,
            question = "何時間くらい連続で動かしますか？",
            appliesWhen = { it.hasActuator() },
            safeDefault = { "continuous_for_conservative_sizing" },
        ),
        RequirementSlotDefinition(
            id = "req_precision",
            necessity = RequirementNecessity.DEFAULTABLE,
            safetyCritical = false,
            question = "精度や応答速度に希望はありますか？",
            safeDefault = { "practical_verified_mid_range" },
        ),
    )

    private fun RequirementContext.hasActuator(): Boolean =
        isTrue("has_actuator") ||
            goalContainsAny(
                "ファン", "モーター", "ポンプ", "サーボ", "リレー", "ヒーター",
                "fan", "motor", "pump", "servo", "relay", "heater",
            )
}

class DefaultRequirementResolver(
    private val definitions: List<RequirementSlotDefinition> = DefaultRequirementCatalog.definitions,
) : RequirementResolver {

    override fun resolve(
        intent: IntentDraft,
        existing: ResolvedRequirements?,
    ): RequirementResolution {
        val goal = intent.interpretedGoal?.trim()?.takeIf { it.isNotEmpty() }
            ?: existing?.goal?.trim()?.takeIf { it.isNotEmpty() }
            ?: intent.extractedFacts["goal"]?.trim()?.takeIf { it.isNotEmpty() }

        if (goal == null) {
            return RequirementResolution.NeedUserInput(
                listOf(
                    MissingRequirement(
                        slotId = "req_goal",
                        reason = "core goal is missing",
                        safetyCritical = false,
                        blocking = true,
                        userQuestion = "何を自動化・監視・操作したいですか？",
                    )
                )
            )
        }

        val resolved = linkedMapOf<String, RequirementValue>()
        existing?.slots?.let(resolved::putAll)

        intent.extractedFacts
            .filterKeys { it != "goal" }
            .forEach { (key, value) ->
                resolved[key] = RequirementValue(
                    value = value,
                    source = RequirementSource.USER,
                    confidence = 1.0,
                )
            }

        val missing = mutableListOf<MissingRequirement>()

        definitions.forEach { definition ->
            if (resolved.containsKey(definition.id)) return@forEach

            val context = RequirementContext(goal, resolved)
            if (!definition.appliesWhen(context)) return@forEach

            val defaultValue = definition.safeDefault?.invoke(context)
            if (defaultValue != null) {
                resolved[definition.id] = RequirementValue(
                    value = defaultValue,
                    source = RequirementSource.INFERRED_SAFE_DEFAULT,
                    confidence = 0.90,
                )
                return@forEach
            }

            if (definition.necessity == RequirementNecessity.REQUIRED) {
                missing += MissingRequirement(
                    slotId = definition.id,
                    reason = "required condition cannot be safely derived",
                    safetyCritical = definition.safetyCritical,
                    blocking = true,
                    userQuestion = definition.question,
                )
            }
        }

        val requirements = ResolvedRequirements(
            goal = goal,
            slots = resolved,
            assumptions = existing?.assumptions.orEmpty(),
            unresolved = missing,
            requestedComponents =
                RequestedComponentExtractor.extract(goal),
        )

        val blocking = missing.filter { it.blocking }
        return if (blocking.isNotEmpty()) {
            RequirementResolution.NeedUserInput(blocking)
        } else {
            RequirementResolution.Ready(requirements)
        }
    }
}
