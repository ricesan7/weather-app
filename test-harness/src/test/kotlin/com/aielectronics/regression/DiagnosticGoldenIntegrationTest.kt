package com.aielectronics.regression

import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.diagnostics.DiagnosticCorrelator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class DiagnosticGoldenIntegrationTest {

    private val interpreter = BeginnerIntentInterpreter()
    private val engine = ApplicationProjectEngine()
    private val correlator = DiagnosticCorrelator()

    @Test
    fun `golden diagnostics reference actual circuit connections and build steps`() {
        val intent = interpreter.interpret(
            "温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。"
        )
        val requirements = assertIs<RequirementResolution.Ready>(
            engine.resolve(intent)
        ).requirements
        val bundle = assertIs<CompileResult.Success>(
            engine.compile(requirements)
        ).bundle

        val actualConnectionIds = bundle.circuitGraph.connections
            .map { it.id }
            .toSet()

        assertEquals(
            setOf("diag_sensor_missing", "diag_fan_not_rotating"),
            bundle.designIr.diagnostics.map { it.id }.toSet(),
        )

        bundle.designIr.diagnostics.forEach { diagnostic ->
            assertTrue(diagnostic.buildStepIds.isNotEmpty())
            diagnostic.buildStepIds.forEach { ref ->
                assertTrue(ref.startsWith("connection:"))
                assertTrue(
                    ref.removePrefix("connection:") in actualConnectionIds,
                    "Unknown diagnostic connection ref: $ref",
                )
            }
        }

        val sensor = correlator.correlateTestFailure(
            testId = "sensor_probe",
            diagnostics = bundle.designIr.diagnostics,
            diagramSpec = bundle.diagramSpec,
        )
        requireNotNull(sensor)
        assertTrue(sensor.steps.isNotEmpty())
        assertTrue(sensor.steps.all { it.connectionId in actualConnectionIds })

        val fan = correlator.correlateTestFailure(
            testId = "fan_output_test",
            diagnostics = bundle.designIr.diagnostics,
            diagramSpec = bundle.diagramSpec,
        )
        requireNotNull(fan)
        assertTrue(fan.steps.isNotEmpty())
        assertTrue(fan.steps.all { it.connectionId in actualConnectionIds })
    }

    @Test
    fun `sensor diagnosis is limited to sensor power ground and i2c wiring`() {
        val bundle = goldenBundle()

        val sensor = requireNotNull(
            correlator.correlateTestFailure(
                "sensor_probe",
                bundle.designIr.diagnostics,
                bundle.diagramSpec,
            )
        )

        val relevantWires = bundle.diagramSpec.wires
            .filter { it.connectionId in sensor.connectionIds }

        assertEquals(4, relevantWires.size)
        assertEquals(
            setOf(
                com.aielectronics.core.model.NetType.POWER,
                com.aielectronics.core.model.NetType.GROUND,
                com.aielectronics.core.model.NetType.I2C_SDA,
                com.aielectronics.core.model.NetType.I2C_SCL,
            ),
            relevantWires.map { it.netType }.toSet(),
        )
    }

    private fun goldenBundle(): com.aielectronics.core.model.ReleaseBundle {
        val intent = interpreter.interpret(
            "温湿度を記録し、30℃以上でファン、28℃以下で停止。"
        )
        val requirements = assertIs<RequirementResolution.Ready>(
            engine.resolve(intent)
        ).requirements
        return assertIs<CompileResult.Success>(
            engine.compile(requirements)
        ).bundle
    }
}
