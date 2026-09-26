package com.aielectronics.regression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ZeroConfigGateTest {

    @Test
    fun `all fourteen release blocking zero config rules pass for golden flow`() {
        val report = ZeroConfigGate().evaluateGoldenFlow()

        assertEquals(14, report.rules.size)
        assertTrue(
            report.passed,
            report.rules
                .filterNot { it.passed }
                .joinToString("\n") {
                    it.ruleId + " " + it.metric +
                        " actual=" + it.actual +
                        " target=" + it.target
                },
        )
    }

    @Test
    fun `golden flow has no manual technical decisions`() {
        val metrics = ZeroConfigGate().evaluateGoldenFlow().metrics

        assertEquals(0, metrics.manualProgrammingActions)
        assertEquals(0, metrics.manualPinDecisions)
        assertEquals(0, metrics.manualLibraryChoices)
        assertEquals(0, metrics.manualBuildConfig)
        assertEquals(0, metrics.technicalModeChoices)
        assertEquals(0, metrics.requiredSchematicReading)
        assertEquals(0, metrics.rebuildsForNormalSettings)
        assertEquals(0, metrics.nonessentialQuestions)
        assertEquals(0, metrics.beginnerRawErrorDependency)
        assertEquals(0, metrics.workflowAppSwitches)
        assertEquals(0, metrics.requiredDomainTerms)
        assertEquals(1, metrics.simultaneousBuildDecisions)
        assertTrue(metrics.beginnerRoleCategories <= 4)
        assertTrue(metrics.explainabilityAvailable)
    }
}
