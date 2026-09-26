package com.aielectronics.diagnostics

import com.aielectronics.core.model.DiagnosticSpec
import com.aielectronics.core.model.DiagramSpec
import com.aielectronics.core.model.GuidedBuildPlan
import com.aielectronics.core.model.GuidedBuildStep
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class DiagnosticCorrelatorTest {

    @Test
    fun `failed test resolves exact guided build steps`() {
        val diagram = DiagramSpec(
            views = emptyList(),
            buildPlan = GuidedBuildPlan(
                listOf(
                    step(1, "conn_001"),
                    step(2, "conn_002"),
                    step(3, "conn_003"),
                )
            ),
        )
        val diagnostics = listOf(
            DiagnosticSpec(
                id = "diag_sensor_missing",
                trigger = "sensor_probe_failed",
                likelyCauses = listOf("VDD/GND", "SDA/SCL"),
                buildStepIds = listOf(
                    "connection:conn_003",
                    "connection:conn_001",
                ),
            )
        )

        val finding = DiagnosticCorrelator().correlateTestFailure(
            testId = "sensor_probe",
            diagnostics = diagnostics,
            diagramSpec = diagram,
        )

        requireNotNull(finding)
        assertEquals(
            listOf(1, 3),
            finding.steps.map { it.order },
        )
        assertEquals(
            listOf("conn_001", "conn_003"),
            finding.connectionIds,
        )
    }

    @Test
    fun `unknown test has no diagnostic finding`() {
        assertNull(
            DiagnosticCorrelator().correlateTestFailure(
                testId = "unknown",
                diagnostics = emptyList(),
                diagramSpec = DiagramSpec(emptyList()),
            )
        )
    }

    private fun step(order: Int, connectionId: String) =
        GuidedBuildStep(
            order = order,
            connectionId = connectionId,
            title = "配線 $order",
            instruction = "$connectionId を接続",
            diagramViewId = "step_$order",
        )
}
