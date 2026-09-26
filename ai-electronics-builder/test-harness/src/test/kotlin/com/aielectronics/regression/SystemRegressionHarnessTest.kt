package com.aielectronics.regression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SystemRegressionHarnessTest {

    private val harness = SystemRegressionHarness()

    @Test
    fun `all eight practical fixtures end in compiled or explicit unsupported state`() {
        val results = harness.runAll()

        assertEquals(8, results.size)
        assertTrue(
            results.all {
                it is RegressionOutcome.Compiled ||
                    it is RegressionOutcome.Unsupported
            },
            results.joinToString("\n") { result ->
                result.fixture.id + " -> " + result::class.simpleName
            },
        )
    }

    @Test
    fun `environment ventilation fixture compiles through real application engine`() {
        val result = harness.run(
            SystemRegressionFixtures.all.single { it.id == "reg_env_01" }
        )
        val compiled = assertIs<RegressionOutcome.Compiled>(result)

        assertEquals("xiao_esp32s3", compiled.bundle.designIr.board.boardId)
        assertEquals("30.0", compiled.bundle.designIr.settings
            .single { it.id == "temp_on" }
            .defaultValue)
        assertEquals("28.0", compiled.bundle.designIr.settings
            .single { it.id == "temp_off" }
            .defaultValue)
        assertTrue(compiled.bundle.diagramSpec.wires.isNotEmpty())
        assertTrue(compiled.bundle.testPlan.tests.isNotEmpty())
        assertTrue(compiled.bundle.manifest != null)
    }

    @Test
    fun `unsupported fixtures expose exactly what is missing`() {
        val results = harness.runAll()
            .filterIsInstance<RegressionOutcome.Unsupported>()

        assertEquals(7, results.size)

        val irrigation = results.single {
            it.fixture.id == "reg_irrigation_01"
        }
        assertTrue("schedule" in irrigation.missingFeatures)
        assertTrue("interlock" in irrigation.missingFeatures)
        assertTrue("soil_sensor" in irrigation.missingHardware)
        assertTrue(irrigation.reason.startsWith("unsupported:"))

        val advanced = results.single {
            it.fixture.id == "reg_adv_01"
        }
        assertTrue("advanced_code" in advanced.missingFeatures)
        assertTrue("validated_project" in advanced.missingHardware)
    }
}
