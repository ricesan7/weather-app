package com.aielectronics.builder

import com.aielectronics.compiler.*
import com.aielectronics.core.model.*
import com.aielectronics.diagram.DefaultDiagramCompiler
import com.aielectronics.parts.GoldenEngineeringCatalog
import kotlin.math.roundToInt

class BeginnerIntentInterpreter {

    fun interpret(
        text: String,
        clarifications: Map<String, String> = emptyMap(),
    ): IntentDraft {
        val normalized = text.trim()
        val facts = linkedMapOf<String, String>()

        val hasActuator = normalized.containsAny(
            "ファン", "換気", "モーター", "ポンプ", "サーボ", "リレー",
            "fan", "motor", "pump", "servo", "relay",
        )
        if (hasActuator) facts["has_actuator"] = "true"

        val automation = normalized.containsAny(
            "自動", "なったら", "以上", "以下", "応じて",
            "automatic", "when", "above", "below",
        )
        if (automation) {
            facts["automation_required"] = "true"
            facts["automation_rule"] = normalized
        }

        if (normalized.containsAny("履歴", "記録", "ログ", "グラフ", "history", "log")) {
            facts["logging_requested"] = "true"
        }

        temperatureThreshold(normalized)?.let { value ->
            facts["temp_on"] = formatNumber(value)
            facts["temp_off"] = formatNumber(value - 2.0)
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
            Regex("""(\d+(?:\.\d+)?)\s*(?:℃|°C)[^。\n]{0,12}(?:以上|超|より高|above)""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }
    }

    private fun humidityThreshold(text: String): Double? {
        val patterns = listOf(
            Regex("""(?:湿度|humidity)[^0-9]{0,12}(\d+(?:\.\d+)?)""", RegexOption.IGNORE_CASE),
            Regex("""(\d+(?:\.\d+)?)\s*%[^。\n]{0,12}(?:以上|超|above)""", RegexOption.IGNORE_CASE),
        )
        return patterns.firstNotNullOfOrNull { regex ->
            regex.find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
        }
    }

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

class AppProjectEngine(
    private val catalog: com.aielectronics.parts.EngineeringCatalog =
        GoldenEngineeringCatalog,
) {
    private val requirementResolver = DefaultRequirementResolver()
    private val diagramCompiler = DefaultDiagramCompiler(catalog)

    private val compiler = DefaultProjectCompiler(
        capabilityMapper = DefaultCapabilityMapper(),
        componentResolver = CatalogComponentResolver(catalog),
        boardSelector = CatalogBoardSelector(catalog),
        powerPlanner = CatalogPowerPlanner(catalog),
        pinAllocator = CatalogPinAllocator(catalog),
        circuitCompiler = CatalogCircuitCompiler(catalog),
        electricalValidator = CatalogElectricalValidator(catalog),
        behaviorCompiler = DefaultBehaviorCompiler(),
        coreAssembler = DefaultDesignCoreAssembler(),
        diagramCompiler = object : DiagramCompiler {
            override fun compile(graph: CircuitGraph): Result<DiagramSpec> =
                diagramCompiler.compile(graph)
        },
        manifestCompiler = DefaultManifestCompiler(catalog),
        uiCompiler = DefaultUiCompiler(),
        diagnosticCompiler = DefaultDiagnosticCompiler(),
    )

    fun resolve(intent: IntentDraft): RequirementResolution =
        requirementResolver.resolve(intent)

    fun compile(requirements: ResolvedRequirements): CompileResult =
        compiler.compile(requirements)
}
