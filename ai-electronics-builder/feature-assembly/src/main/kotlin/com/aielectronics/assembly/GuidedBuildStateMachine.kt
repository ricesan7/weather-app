package com.aielectronics.assembly

import com.aielectronics.core.model.GuidedBuildPlan
import com.aielectronics.core.model.GuidedBuildStep

enum class GuidedBuildStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETE,
}

data class GuidedBuildSnapshot(
    val completedConnectionIds: Set<String> = emptySet(),
    val currentStepIndex: Int = 0,
)

data class GuidedBuildState(
    val status: GuidedBuildStatus,
    val currentStep: GuidedBuildStep?,
    val completedConnectionIds: Set<String>,
    val completedCount: Int,
    val totalCount: Int,
) {
    val progress: Double
        get() = if (totalCount == 0) 1.0 else completedCount.toDouble() / totalCount
}

class GuidedBuildStateMachine(
    private val plan: GuidedBuildPlan,
    snapshot: GuidedBuildSnapshot = GuidedBuildSnapshot(),
) {
    private val completed = snapshot.completedConnectionIds.toMutableSet()
    private var index = snapshot.currentStepIndex.coerceIn(
        0,
        (plan.steps.size - 1).coerceAtLeast(0),
    )

    fun state(): GuidedBuildState {
        val complete = completed.containsAll(plan.steps.map { it.connectionId })
        val current = if (complete || plan.steps.isEmpty()) null else plan.steps[index]

        return GuidedBuildState(
            status = when {
                complete -> GuidedBuildStatus.COMPLETE
                completed.isEmpty() && index == 0 -> GuidedBuildStatus.NOT_STARTED
                else -> GuidedBuildStatus.IN_PROGRESS
            },
            currentStep = current,
            completedConnectionIds = completed.toSet(),
            completedCount = completed.size,
            totalCount = plan.steps.size,
        )
    }

    fun markCurrentCompleted(): GuidedBuildState {
        val step = plan.steps.getOrNull(index) ?: return state()
        completed += step.connectionId

        if (index < plan.steps.lastIndex) {
            index += 1
            while (index < plan.steps.lastIndex &&
                plan.steps[index].connectionId in completed
            ) {
                index += 1
            }
        }
        return state()
    }

    fun back(): GuidedBuildState {
        if (index > 0) index -= 1
        return state()
    }

    fun goToStep(order: Int): GuidedBuildState {
        require(order in 1..plan.steps.size) { "Invalid step order" }
        index = order - 1
        return state()
    }

    fun snapshot(): GuidedBuildSnapshot =
        GuidedBuildSnapshot(
            completedConnectionIds = completed.toSet(),
            currentStepIndex = index,
        )
}
