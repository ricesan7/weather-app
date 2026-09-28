package com.aielectronics.regression

import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.application.InMemoryProjectRepository
import com.aielectronics.application.ProjectTitle
import com.aielectronics.application.SavedProject
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.application.SavedGraphNodePosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ProjectPersistenceRegressionTest {

    @Test
    fun `saved project recompiles and resumes at the first unfinished wiring step`() {
        val interpreter = BeginnerIntentInterpreter()
        val engine = ApplicationProjectEngine()
        val repository = InMemoryProjectRepository()
        val goal =
            "温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。"

        val originalResolution = engine.resolve(interpreter.interpret(goal))
        val originalRequirements =
            (originalResolution as RequirementResolution.Ready).requirements
        val originalCompile =
            engine.compile(originalRequirements) as CompileResult.Success
        val originalBundle = originalCompile.bundle
        val steps = requireNotNull(originalBundle.diagramSpec.buildPlan).steps
        val completed = steps.take(4).map { it.connectionId }.toSet()

        repository.save(
            SavedProject(
                id = "resume-test",
                title = ProjectTitle.fromGoal(goal),
                goalText = goal,
                clarificationValues = emptyMap(),
                completedConnectionIds = completed,
                currentBuildStepIndex = 4,
                lastScreen = "BUILD",
                deployed = false,
                createdAtEpochMs = 1000,
                updatedAtEpochMs = 2000,
                graphNodePositions = mapOf(
                    "hardware.board" to SavedGraphNodePosition(0.12, 0.42),
                ),
            )
        )

        val restored = assertNotNull(repository.load("resume-test"))
        val resumedResolution = engine.resolve(
            interpreter.interpret(
                text = restored.goalText,
                clarifications = restored.clarificationValues,
            )
        )
        val resumedRequirements =
            (resumedResolution as RequirementResolution.Ready).requirements
        val resumedCompile =
            engine.compile(resumedRequirements) as CompileResult.Success
        val resumedBundle = resumedCompile.bundle
        val resumedSteps = requireNotNull(resumedBundle.diagramSpec.buildPlan).steps

        assertEquals(
            originalBundle.circuitGraph.connections.map { it.id },
            resumedBundle.circuitGraph.connections.map { it.id },
        )
        assertEquals(completed, restored.completedConnectionIds)
        assertEquals(4, restored.currentBuildStepIndex)
        assertEquals(
            SavedGraphNodePosition(0.12, 0.42),
            restored.graphNodePositions["hardware.board"],
        )
        assertTrue(resumedSteps[4].connectionId !in restored.completedConnectionIds)
        assertEquals(
            steps[4].connectionId,
            resumedSteps[restored.currentBuildStepIndex].connectionId,
        )
    }

    @Test
    fun `saved project summaries are newest first and contain no compiled electrical snapshot`() {
        val repository = InMemoryProjectRepository()

        repository.save(
            SavedProject(
                id = "old",
                title = "旧プロジェクト",
                goalText = "温度を測りたい",
                clarificationValues = emptyMap(),
                completedConnectionIds = emptySet(),
                currentBuildStepIndex = 0,
                lastScreen = "DESIGN",
                deployed = false,
                createdAtEpochMs = 1000,
                updatedAtEpochMs = 1000,
            )
        )
        repository.save(
            SavedProject(
                id = "new",
                title = "新プロジェクト",
                goalText = "温度でファンを回したい",
                clarificationValues = mapOf("req_power_source" to "usb_5v"),
                completedConnectionIds = setOf("conn_1", "conn_2"),
                currentBuildStepIndex = 2,
                lastScreen = "BUILD",
                deployed = false,
                createdAtEpochMs = 2000,
                updatedAtEpochMs = 3000,
            )
        )

        val summaries = repository.list()

        assertEquals(listOf("new", "old"), summaries.map { it.id })
        assertEquals(2, summaries.first().completedConnectionCount)
        assertEquals("BUILD", summaries.first().lastScreen)
    }
}
