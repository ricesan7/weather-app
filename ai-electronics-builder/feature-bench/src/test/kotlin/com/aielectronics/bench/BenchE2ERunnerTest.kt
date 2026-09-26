package com.aielectronics.bench

import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.runtime.RuntimeFrame
import com.aielectronics.runtime.RuntimeMessageType
import com.aielectronics.runtime.RuntimeTransport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BenchE2ERunnerTest {

    @Test
    fun `golden bench gate passes all six live protocol stages`() {
        val bundle = goldenBundle()
        val transport = FakeRuntimeTransport(bundle)

        val report = BenchE2ERunner(transport).run(bundle)

        assertTrue(report.passed)
        assertEquals(6, report.steps.size)
        assertTrue(report.steps.all { it.status == BenchStepStatus.PASS })
        assertTrue(report.telemetry.isNotEmpty())
        assertTrue(report.testedSettingId != null)
        assertTrue(
            transport.requests.any {
                it.type == RuntimeMessageType.DEPLOY_MANIFEST
            }
        )
        assertTrue(
            transport.requests.any {
                it.type == RuntimeMessageType.SET_VALUE
            }
        )
    }

    @Test
    fun `failed required self test fails gate before telemetry`() {
        val bundle = goldenBundle()
        val failingTest = bundle.manifest!!
            .tests
            .first { it.required }
            .id
        val transport = FakeRuntimeTransport(
            bundle = bundle,
            failingTestId = failingTest,
        )

        val report = BenchE2ERunner(transport).run(bundle)

        assertFalse(report.passed)
        assertEquals(
            BenchStepId.SELF_TESTS,
            report.steps.last().id,
        )
        assertEquals(
            BenchStepStatus.FAIL,
            report.steps.last().status,
        )
        assertTrue(
            transport.requests.none {
                it.type == RuntimeMessageType.TELEMETRY
            }
        )
    }

    @Test
    fun `missing required telemetry fails gate`() {
        val bundle = goldenBundle()
        val missingId = bundle.manifest!!
            .telemetryIds
            .first()
        val transport = FakeRuntimeTransport(
            bundle = bundle,
            missingTelemetryId = missingId,
        )

        val report = BenchE2ERunner(transport).run(bundle)

        assertFalse(report.passed)
        assertEquals(
            BenchStepId.TELEMETRY,
            report.steps.last().id,
        )
        assertTrue(report.steps.last().message.contains(missingId))
    }

    @Test
    fun `missing runtime capability fails at handshake`() {
        val bundle = goldenBundle()
        val transport = FakeRuntimeTransport(
            bundle = bundle,
            disabledCapability = "telemetry",
        )

        val report = BenchE2ERunner(transport).run(bundle)

        assertFalse(report.passed)
        assertEquals(1, report.steps.size)
        assertEquals(BenchStepId.HANDSHAKE, report.steps.single().id)
        assertEquals(BenchStepStatus.FAIL, report.steps.single().status)
    }

    private fun goldenBundle(): ReleaseBundle {
        val interpreter = BeginnerIntentInterpreter()
        val engine = ApplicationProjectEngine()

        val intent = interpreter.interpret(
            "温湿度を記録し、30℃以上でファン、28℃以下で停止。スマホから設定変更したい。"
        )
        val resolution = engine.resolve(intent) as RequirementResolution.Ready
        return (engine.compile(resolution.requirements) as CompileResult.Success).bundle
    }

    private class FakeRuntimeTransport(
        bundle: ReleaseBundle,
        private val failingTestId: String? = null,
        private val missingTelemetryId: String? = null,
        private val disabledCapability: String? = null,
    ) : RuntimeTransport {
        val requests = mutableListOf<RuntimeFrame>()
        private val manifest = requireNotNull(bundle.manifest)
        private val settings = manifest.settings.associate {
            it.id to it.defaultValue
        }.toMutableMap()

        override fun exchange(frame: RuntimeFrame): Result<RuntimeFrame> =
            runCatching {
                requests += frame

                when (frame.type) {
                    RuntimeMessageType.HELLO ->
                        response(
                            frame,
                            RuntimeMessageType.CAPABILITIES,
                            mapOf(
                                "protocol_version" to "1",
                                "manifest" to enabled("manifest"),
                                "settings" to enabled("settings"),
                                "self_test" to enabled("self_test"),
                                "telemetry" to enabled("telemetry"),
                            ),
                        )

                    RuntimeMessageType.DEPLOY_MANIFEST ->
                        response(
                            frame,
                            RuntimeMessageType.DEPLOY_RESULT,
                            mapOf("ok" to "true"),
                        )

                    RuntimeMessageType.VERIFY_PROJECT ->
                        response(
                            frame,
                            RuntimeMessageType.VERIFY_RESULT,
                            mapOf("ok" to "true"),
                        )

                    RuntimeMessageType.RUN_TEST -> {
                        val id = frame.fields.getValue("test_id")
                        response(
                            frame,
                            RuntimeMessageType.TEST_RESULT,
                            mapOf(
                                "test_id" to id,
                                "passed" to (id != failingTestId).toString(),
                            ),
                        )
                    }

                    RuntimeMessageType.TELEMETRY -> {
                        val values = manifest.telemetryIds
                            .associateWith { id ->
                                when {
                                    id.contains("temperature") -> "30.5"
                                    id.contains("humidity") -> "55"
                                    id.contains("fan") -> "OFF"
                                    else -> "1"
                                }
                            }
                            .filterKeys { it != missingTelemetryId }

                        response(
                            frame,
                            RuntimeMessageType.TELEMETRY,
                            values,
                        )
                    }

                    RuntimeMessageType.GET_VALUE -> {
                        val id = frame.fields.getValue("setting_id")
                        response(
                            frame,
                            RuntimeMessageType.VALUE,
                            mapOf(
                                "setting_id" to id,
                                "value" to settings.getValue(id),
                            ),
                        )
                    }

                    RuntimeMessageType.SET_VALUE -> {
                        val id = frame.fields.getValue("setting_id")
                        val value = frame.fields.getValue("value")
                        settings[id] = value
                        response(
                            frame,
                            RuntimeMessageType.VALUE,
                            mapOf(
                                "setting_id" to id,
                                "value" to value,
                                "persisted" to "true",
                            ),
                        )
                    }

                    else ->
                        response(
                            frame,
                            RuntimeMessageType.ERROR,
                            mapOf(
                                "code" to "E_UNEXPECTED",
                                "message" to "unexpected test request",
                            ),
                        )
                }
            }

        private fun enabled(name: String): String =
            (name != disabledCapability).toString()

        private fun response(
            request: RuntimeFrame,
            type: RuntimeMessageType,
            fields: Map<String, String>,
        ): RuntimeFrame =
            RuntimeFrame(
                protocolVersion = request.protocolVersion,
                type = type,
                requestId = request.requestId,
                fields = fields,
            )
    }
}
