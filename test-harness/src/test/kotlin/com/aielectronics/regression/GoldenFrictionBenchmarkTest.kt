package com.aielectronics.regression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GoldenFrictionBenchmarkTest {

    @Test
    fun `golden beginner flow stays inside friction budget`() {
        val result = GoldenFrictionBenchmark().run()

        assertTrue(
            result.budgetPassed,
            result.budgetFailures.joinToString("\n"),
        )

        val metrics = result.snapshot
        assertEquals(0, metrics.questionsPresented)
        assertEquals(0, metrics.technicalChoicesPresented)
        assertEquals(0, metrics.manualTechnicalSettings)
        assertEquals(6, metrics.screenTransitions)
        assertEquals(4, metrics.userInitiatedScreenTransitions)
        assertEquals(0, metrics.avoidableTechnicalActions)
    }

    @Test
    fun `physical assembly actions stay separate from technical friction`() {
        val result = GoldenFrictionBenchmark().run()
        val metrics = result.snapshot

        assertEquals(
            result.bundle.circuitGraph.connections.size,
            metrics.guidedBuildConfirmations,
        )
        assertEquals(10, metrics.guidedBuildConfirmations)
        assertEquals(1, metrics.goalSubmissions)
        assertEquals(1, metrics.deviceConnectActions)
        assertEquals(1, metrics.deployActions)
        assertEquals(17, metrics.essentialUserActions)
    }
}
