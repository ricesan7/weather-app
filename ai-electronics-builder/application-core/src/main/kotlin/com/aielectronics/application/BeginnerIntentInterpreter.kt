package com.aielectronics.application

import com.aielectronics.core.model.IntentDraft
import kotlin.math.roundToInt

class BeginnerIntentInterpreter {

    fun interpret(
        text: String,
        clarifications: Map<String, String> = emptyMap(),
    ): IntentDraft {
        val normalized = text.trim()
        val facts = linkedMapOf<String, String>()

        val hasActuator = normalized.containsAny(
            "ファン", "換気", "モーター", "ポンプ", "サーボ", "リレー", "ヒーター",
            "fan", "motor", "pump", "servo", "relay", "heater",
        )
        if (hasActuator) facts["has_actuator"] = "true"

        val automation = normalized.containsAny(
            "自動", "なったら", "以上", "以下", "応じて", "必ず停止",
            "automatic", "when", "above", "below", "interlock",
        )
        if (automation) {
            facts["automation_required"] = "true"
            facts["automation_rule"] = normalized
        }

        if (normalized.containsAny("履歴", "記録", "ログ", "グラフ", "保存", "history", "log")) {
            facts["logging_requested"] = "true"
        }

        temperatureThreshold(normalized)?.let { value ->
            facts["temp_on"] = formatNumber(value)
            if (!normalized.containsExplicitOffThreshold()) {
                facts["temp_off"] = formatNumber(value - 2.0)
            }
        }

        temperatureOffThreshold(normalized)?.let { value ->
            facts["temp_off"] = formatNumber(value)
        }

        humidityThreshold(normalized)?.let { value ->
            facts["rh_on"] = formatNumber(value)
            facts["rh_off"] = formatNumber((value - 5.0).coerceAtLeast(0.0))
        }

        facts.putAll(clarifications)

        return IntentDraft(
            rawText = normalized,
            interpretedGoal = normalized.ifBlank { null },
            extractedFacts = facts,
        )
    }

    private fun temperatureThreshold(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:温度|temperature)[^0-9]{0,12}(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)\s*(?:℃|°C)\s*(?:以上|超|より高|above)""", RegexOption.IGNORE_CASE),
        )
        return latestNumberMatch(text, patterns)
    }

    private fun temperatureOffThreshold(text: String): Double? {
        val patterns = listOf(
            Regex(
                """(\d+(?:\.\d+)?)\s*(?:℃|°C)\s*(?:以下|未満)""",
                RegexOption.IGNORE_CASE,
            ),
            Regex(
                """(\d+(?:\.\d+)?)\s*(?:℃|°C)[^0-9℃°]{0,10}(?:停止|止め)""",
                RegexOption.IGNORE_CASE,
            ),
        )
        return latestNumberMatch(text, patterns)
    }

    private fun humidityThreshold(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:湿度|humidity)[^0-9]{0,12}(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)\s*%[^。\n]{0,12}(?:以上|超|above)""", RegexOption.IGNORE_CASE),
        )
        return latestNumberMatch(text, patterns)
    }

    private fun latestNumberMatch(
        text: String,
        patterns: List<Regex>,
    ): Double? =
        patterns
            .flatMap { regex ->
                regex.findAll(text)
                    .mapNotNull { match ->
                        match.groupValues.getOrNull(1)
                            ?.toDoubleOrNull()
                            ?.let { value -> match.range.first to value }
                    }
                    .toList()
            }
            .maxByOrNull { it.first }
            ?.second

    private fun String.containsExplicitOffThreshold(): Boolean =
        Regex(
            """\d+(?:\.\d+)?\s*(?:℃|°C)\s*(?:以下|未満)""",
            RegexOption.IGNORE_CASE,
        ).containsMatchIn(this) ||
            Regex(
                """\d+(?:\.\d+)?\s*(?:℃|°C)[^0-9℃°]{0,10}(?:停止|止め)""",
                RegexOption.IGNORE_CASE,
            ).containsMatchIn(this)

    private fun String.containsAny(vararg terms: String): Boolean {
        val lowered = lowercase()
        return terms.any { lowered.contains(it.lowercase()) }
    }

    private fun formatNumber(value: Double): String =
        if (value == value.roundToInt().toDouble()) {
            value.roundToInt().toString() + ".0"
        } else {
            value.toString()
        }
}
