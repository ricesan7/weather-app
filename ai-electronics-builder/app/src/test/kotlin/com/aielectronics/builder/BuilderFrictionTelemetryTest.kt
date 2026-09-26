package com.aielectronics.builder

import com.aielectronics.application.FrictionTelemetryRecorder
import kotlin.test.Test
import kotlin.test.assertEquals

class BuilderFrictionTelemetryTest {

    @Test
    fun `view model records user navigation transitions`() {
        val telemetry = FrictionTelemetryRecorder()
        val viewModel = BuilderAppViewModel(
            frictionTelemetry = telemetry,
        )

        viewModel.open(AppScreen.DESIGN)
        viewModel.open(AppScreen.PARTS)

        val snapshot = viewModel.friction.value
        assertEquals(2, snapshot.screenTransitions)
        assertEquals(2, snapshot.userInitiatedScreenTransitions)
    }

    @Test
    fun `guided build confirmation is measured as physical work`() {
        val telemetry = FrictionTelemetryRecorder()
        val viewModel = BuilderAppViewModel(
            frictionTelemetry = telemetry,
        )

        viewModel.confirmBuildStep("wire-1")
        viewModel.confirmBuildStep("wire-2")

        val snapshot = viewModel.friction.value
        assertEquals(2, snapshot.guidedBuildConfirmations)
        assertEquals(0, snapshot.avoidableTechnicalActions)
    }
}
