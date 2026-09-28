package com.aielectronics.regression

import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerErrorPresenter
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.application.BeginnerJourneyContract
import com.aielectronics.application.DesignExplanationBuilder
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.DiagramViewKind
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.Severity
import com.aielectronics.core.model.ValidationIssue
import com.aielectronics.core.model.ValidationReport
import com.aielectronics.core.model.ValidationState

data class ZeroConfigMetrics(
    val manualProgrammingActions: Int,
    val manualPinDecisions: Int,
    val manualLibraryChoices: Int,
    val manualBuildConfig: Int,
    val technicalModeChoices: Int,
    val requiredSchematicReading: Int,
    val rebuildsForNormalSettings: Int,
    val nonessentialQuestions: Int,
    val simultaneousBuildDecisions: Int,
    val beginnerRawErrorDependency: Int,
    val workflowAppSwitches: Int,
    val beginnerRoleCategories: Int,
    val requiredDomainTerms: Int,
    val explainabilityAvailable: Boolean,
)

data class ZeroConfigRuleResult(
    val ruleId: String,
    val metric: String,
    val actual: String,
    val target: String,
    val passed: Boolean,
)

data class ZeroConfigGateReport(
    val metrics: ZeroConfigMetrics,
    val rules: List<ZeroConfigRuleResult>,
) {
    val passed: Boolean get() = rules.all { it.passed }
}

class ZeroConfigGate(
    private val interpreter: BeginnerIntentInterpreter = BeginnerIntentInterpreter(),
    private val engine: ApplicationProjectEngine = ApplicationProjectEngine(),
    private val journey: BeginnerJourneyContract = BeginnerJourneyContract(),
) {

    fun evaluateGoldenFlow(): ZeroConfigGateReport {
        val request =
            "温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。"

        val intent = interpreter.interpret(request)
        val resolution = engine.resolve(intent)

        val requirements = when (resolution) {
            is RequirementResolution.Ready -> resolution.requirements
            is RequirementResolution.NeedUserInput ->
                error(
                    "Golden flow unexpectedly asked: " +
                        resolution.missing.joinToString { it.slotId }
                )
        }

        val compile = engine.compile(requirements)
        val bundle = when (compile) {
            is CompileResult.Success -> compile.bundle
            is CompileResult.NeedUserInput ->
                error("Compiler unexpectedly asked user input")
            is CompileResult.NeedComponentResearch ->
                error(
                    "Golden flow unexpectedly requires component research: " +
                        compile.capabilities.joinToString { it.value }
                )
            is CompileResult.Blocked ->
                error("Golden flow blocked by validation")
            is CompileResult.Failed ->
                error("Golden flow compile failed at " + compile.error.stage)
        }

        val technicalQuestionTerms = listOf(
            "gpio", "i2c", "spi", "pwm", "ライブラリ", "driver pack",
            "framework", "flash", "ble", "usb", "wi-fi", "runtime",
        )

        val userQuestionText = requirements.unresolved
            .joinToString(" ") { it.reason }
            .lowercase()

        val mutableSettings = bundle.designIr.settings
            .filter { it.mutableAtRuntime }
            .map { it.id }
            .toSet()
        val manifestMutableSettings = bundle.manifest
            ?.settings
            ?.filter { it.mutableAtRuntime }
            ?.map { it.id }
            ?.toSet()
            .orEmpty()

        val solderViews = bundle.diagramSpec.views
            .filter { it.kind == DiagramViewKind.SOLDER_STEP }
        val maxBuildDecisions = solderViews
            .maxOfOrNull { it.highlightedConnectionIds.size }
            ?: 0

        val sampleHumanError = BeginnerErrorPresenter.validationBlocked(
            ValidationReport(
                state = ValidationState.BLOCKED,
                issues = listOf(
                    ValidationIssue(
                        code = "E_MISSING_COMMON_GND",
                        severity = Severity.CRITICAL,
                        message = "raw internal message",
                        entityIds = setOf("board", "supply"),
                    )
                ),
            )
        )

        val rawErrorVisible =
            Regex("""\b[EW]_[A-Z0-9_]+\b""").containsMatchIn(sampleHumanError) ||
                sampleHumanError.contains("raw internal message")

        val technicalDecisionsInQuestions = technicalQuestionTerms.count {
            userQuestionText.contains(it)
        }

        val buildPlan = bundle.diagramSpec.buildPlan
        val buildPlanCoversAllConnections =
            buildPlan != null &&
                buildPlan.steps.size == bundle.circuitGraph.connections.size &&
                buildPlan.steps.map { it.connectionId }.toSet() ==
                bundle.circuitGraph.connections.map { it.id }.toSet()

        val metrics = ZeroConfigMetrics(
            manualProgrammingActions = journey.manualProgrammingActions,
            manualPinDecisions = if (technicalDecisionsInQuestions == 0) 0 else 1,
            manualLibraryChoices = if (
                requirements.unresolved.none {
                    it.userQuestion.contains("ライブラリ", ignoreCase = true)
                }
            ) 0 else 1,
            manualBuildConfig = journey.manualBuildConfigActions,
            technicalModeChoices = if (
                requirements.unresolved.none {
                    technicalQuestionTerms.any { term ->
                        it.userQuestion.contains(term, ignoreCase = true)
                    }
                }
            ) 0 else 1,
            requiredSchematicReading = if (buildPlanCoversAllConnections) 0 else 1,
            rebuildsForNormalSettings =
                if (manifestMutableSettings.containsAll(mutableSettings)) 0 else 1,
            nonessentialQuestions = requirements.unresolved.count { !it.safetyCritical },
            simultaneousBuildDecisions = maxBuildDecisions,
            beginnerRawErrorDependency = if (rawErrorVisible) 1 else 0,
            workflowAppSwitches = journey.workflowAppSwitches,
            beginnerRoleCategories = journey.userRoleCategories.size,
            requiredDomainTerms = if (
                intent.rawText.contains("I2C", ignoreCase = true) ||
                intent.rawText.contains("PWM", ignoreCase = true) ||
                intent.rawText.contains("GPIO", ignoreCase = true)
            ) 1 else 0,
            explainabilityAvailable =
                DesignExplanationBuilder.explain(bundle).isNotEmpty(),
        )

        return ZeroConfigGateReport(
            metrics = metrics,
            rules = rules(metrics),
        )
    }

    private fun rules(m: ZeroConfigMetrics): List<ZeroConfigRuleResult> = listOf(
        eq("ux_zero_01", "manual_programming_actions", m.manualProgrammingActions, 0),
        eq("ux_zero_02", "manual_pin_decisions", m.manualPinDecisions, 0),
        eq("ux_zero_03", "manual_library_choices", m.manualLibraryChoices, 0),
        eq("ux_zero_04", "manual_build_config", m.manualBuildConfig, 0),
        eq("ux_zero_05", "technical_mode_choices", m.technicalModeChoices, 0),
        eq("ux_zero_06", "required_schematic_reading", m.requiredSchematicReading, 0),
        eq("ux_zero_07", "rebuilds_for_normal_settings", m.rebuildsForNormalSettings, 0),
        eq("ux_zero_08", "nonessential_questions", m.nonessentialQuestions, 0),
        eq("ux_zero_09", "simultaneous_build_decisions", m.simultaneousBuildDecisions, 1),
        eq("ux_zero_10", "beginner_raw_error_dependency", m.beginnerRawErrorDependency, 0),
        eq("ux_zero_11", "workflow_app_switches", m.workflowAppSwitches, 0),
        le("ux_zero_12", "beginner_role_categories", m.beginnerRoleCategories, 4),
        eq("ux_zero_13", "required_domain_terms", m.requiredDomainTerms, 0),
        bool("ux_zero_14", "explainability_available", m.explainabilityAvailable, true),
    )

    private fun eq(
        id: String,
        metric: String,
        actual: Int,
        target: Int,
    ) = ZeroConfigRuleResult(
        ruleId = id,
        metric = metric,
        actual = actual.toString(),
        target = target.toString(),
        passed = actual == target,
    )

    private fun le(
        id: String,
        metric: String,
        actual: Int,
        target: Int,
    ) = ZeroConfigRuleResult(
        ruleId = id,
        metric = metric,
        actual = actual.toString(),
        target = "<=" + target,
        passed = actual <= target,
    )

    private fun bool(
        id: String,
        metric: String,
        actual: Boolean,
        target: Boolean,
    ) = ZeroConfigRuleResult(
        ruleId = id,
        metric = metric,
        actual = actual.toString(),
        target = target.toString(),
        passed = actual == target,
    )
}
