package com.aielectronics.regression

import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.application.FrictionSnapshot
import com.aielectronics.application.FrictionTelemetryRecorder
import com.aielectronics.application.GoldenFrictionBudget
import com.aielectronics.application.evaluate
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.ReleaseBundle

data class GoldenFrictionBenchmarkResult(
    val snapshot: FrictionSnapshot,
    val bundle: ReleaseBundle,
    val budgetPassed: Boolean,
    val budgetFailures: List<String>,
)

class GoldenFrictionBenchmark(
    private val interpreter: BeginnerIntentInterpreter = BeginnerIntentInterpreter(),
    private val engine: ApplicationProjectEngine = ApplicationProjectEngine(),
) {
    fun run(): GoldenFrictionBenchmarkResult {
        val telemetry = FrictionTelemetryRecorder()
        val request =
            "温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。"

        telemetry.recordGoalSubmitted()
        val intent = interpreter.interpret(request)

        val requirements = when (val resolution = engine.resolve(intent)) {
            is RequirementResolution.Ready -> resolution.requirements
            is RequirementResolution.NeedUserInput -> {
                resolution.missing.firstOrNull()?.let {
                    telemetry.recordQuestionPresented(it.slotId)
                }
                error("Golden friction flow unexpectedly requires user input")
            }
        }

        val bundle = when (val compile = engine.compile(requirements)) {
            is CompileResult.Success -> compile.bundle
            is CompileResult.NeedUserInput -> {
                compile.questions.firstOrNull()?.let {
                    telemetry.recordQuestionPresented(it.slotId)
                }
                error("Golden friction compile unexpectedly requires user input")
            }
            is CompileResult.NeedComponentResearch ->
                error(
                    "Golden friction unexpectedly requires component research: " +
                        compile.capabilities.joinToString { it.value }
                )
            is CompileResult.Blocked ->
                error("Golden friction flow blocked")
            is CompileResult.Failed ->
                error("Golden friction flow failed at " + compile.error.stage)
        }

        telemetry.recordScreenTransition("HOME", "DESIGN", userInitiated = false)
        telemetry.recordScreenTransition("DESIGN", "PARTS", userInitiated = true)
        telemetry.recordScreenTransition("PARTS", "WIRING", userInitiated = true)
        telemetry.recordScreenTransition("WIRING", "BUILD", userInitiated = true)

        bundle.diagramSpec.buildPlan
            ?.steps
            .orEmpty()
            .forEach {
                telemetry.recordGuidedBuildConfirmation(it.connectionId)
            }

        telemetry.recordScreenTransition("BUILD", "CONNECT", userInitiated = true)
        telemetry.recordDeviceConnectAction()
        telemetry.recordDeployAction()
        telemetry.recordScreenTransition("CONNECT", "CONTROL", userInitiated = false)

        val snapshot = telemetry.snapshot()
        val budget = GoldenFrictionBudget().evaluate(snapshot)

        return GoldenFrictionBenchmarkResult(
            snapshot = snapshot,
            bundle = bundle,
            budgetPassed = budget.all { it.passed },
            budgetFailures = budget
                .filterNot { it.passed }
                .map {
                    it.metric + " actual=" + it.actual + " target=" + it.target
                },
        )
    }
}
