package com.aielectronics.diagnostics

import com.aielectronics.core.model.DiagnosticSpec
import com.aielectronics.core.model.DiagramSpec
import com.aielectronics.core.model.GuidedBuildStep

data class DiagnosticStepLink(
    val order: Int,
    val connectionId: String,
    val title: String,
    val instruction: String,
    val diagramViewId: String,
)

data class DiagnosticFinding(
    val diagnosticId: String,
    val testId: String,
    val title: String,
    val likelyCauses: List<String>,
    val connectionIds: List<String>,
    val steps: List<DiagnosticStepLink>,
)

class DiagnosticCorrelator {

    fun correlateTestFailure(
        testId: String,
        diagnostics: List<DiagnosticSpec>,
        diagramSpec: DiagramSpec,
    ): DiagnosticFinding? {
        val trigger = testId + "_failed"
        val diagnostic = diagnostics.firstOrNull { it.trigger == trigger }
            ?: return null

        val plan = diagramSpec.buildPlan ?: return null
        val stepsByConnection = plan.steps.associateBy { it.connectionId }

        val connectionIds = diagnostic.buildStepIds
            .mapNotNull(::connectionId)
            .distinct()

        val steps = connectionIds
            .mapNotNull(stepsByConnection::get)
            .sortedBy(GuidedBuildStep::order)
            .map { step ->
                DiagnosticStepLink(
                    order = step.order,
                    connectionId = step.connectionId,
                    title = step.title,
                    instruction = step.instruction,
                    diagramViewId = step.diagramViewId,
                )
            }

        return DiagnosticFinding(
            diagnosticId = diagnostic.id,
            testId = testId,
            title = titleFor(testId),
            likelyCauses = diagnostic.likelyCauses,
            connectionIds = steps.map { it.connectionId },
            steps = steps,
        )
    }

    private fun connectionId(reference: String): String? =
        reference
            .takeIf { it.startsWith("connection:") }
            ?.removePrefix("connection:")
            ?.takeIf(String::isNotBlank)

    private fun titleFor(testId: String): String = when (testId) {
        "sensor_probe" -> "センサー配線を確認してください"
        "fan_output_test" -> "ファン周辺の配線を確認してください"
        else -> "関連する配線を確認してください"
    }
}
