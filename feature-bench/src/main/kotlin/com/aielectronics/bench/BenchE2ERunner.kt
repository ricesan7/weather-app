package com.aielectronics.bench

import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.runtime.CanonicalManifestEncoder
import com.aielectronics.runtime.RuntimeClient
import com.aielectronics.runtime.RuntimeFrame
import com.aielectronics.runtime.RuntimeMessageType
import com.aielectronics.runtime.RuntimeTransport

class BenchE2ERunner(
    private val transport: RuntimeTransport,
) {
    fun run(bundle: ReleaseBundle): BenchReport {
        val manifest = requireNotNull(bundle.manifest) {
            "Bench E2E requires a ProjectManifest"
        }
        val steps = mutableListOf<BenchStepResult>()

        fun pass(id: BenchStepId, message: String) {
            steps += BenchStepResult(id, BenchStepStatus.PASS, message)
        }

        fun fail(
            id: BenchStepId,
            message: String,
            telemetry: Map<String, String> = emptyMap(),
            settingId: String? = null,
        ): BenchReport {
            steps += BenchStepResult(id, BenchStepStatus.FAIL, message)
            return BenchReport(
                projectId = manifest.projectId,
                steps = steps,
                telemetry = telemetry,
                testedSettingId = settingId,
            )
        }

        val hello = exchange(
            RuntimeFrame(
                type = RuntimeMessageType.HELLO,
                requestId = "bench-hello",
            )
        )
        if (hello.type != RuntimeMessageType.CAPABILITIES) {
            return fail(BenchStepId.HANDSHAKE, "CAPABILITIES応答がありません。")
        }

        val requiredCapabilities = listOf(
            "manifest",
            "settings",
            "self_test",
            "telemetry",
        )
        val missingCapabilities = requiredCapabilities.filter {
            hello.fields[it] != "true"
        }
        if (missingCapabilities.isNotEmpty()) {
            return fail(
                BenchStepId.HANDSHAKE,
                "Runtime機能不足: " + missingCapabilities.joinToString(", "),
            )
        }
        pass(BenchStepId.HANDSHAKE, "Runtime handshake正常")

        val client = RuntimeClient(transport)
        client.deployManifest(
            requestId = "bench-deploy",
            manifestVersion = manifest.version,
            payload = CanonicalManifestEncoder().encode(manifest),
            projectId = manifest.projectId,
        ).fold(
            onSuccess = {
                pass(BenchStepId.DEPLOY, "Manifest配布正常")
            },
            onFailure = {
                return fail(
                    BenchStepId.DEPLOY,
                    it.message ?: "Manifest配布失敗",
                )
            },
        )

        client.verifyProject(
            requestId = "bench-verify",
            projectId = manifest.projectId,
        ).fold(
            onSuccess = {
                pass(BenchStepId.VERIFY, "Project verify正常")
            },
            onFailure = {
                return fail(
                    BenchStepId.VERIFY,
                    it.message ?: "Project verify失敗",
                )
            },
        )

        val failedTests = mutableListOf<String>()
        manifest.tests.filter { it.required }.forEach { test ->
            val response = exchange(
                RuntimeFrame(
                    type = RuntimeMessageType.RUN_TEST,
                    requestId = "bench-test-" + test.id,
                    fields = mapOf("test_id" to test.id),
                )
            )
            if (
                response.type != RuntimeMessageType.TEST_RESULT ||
                response.fields["passed"] != "true"
            ) {
                failedTests += test.id
            }
        }
        if (failedTests.isNotEmpty()) {
            return fail(
                BenchStepId.SELF_TESTS,
                "self-test失敗: " + failedTests.joinToString(", "),
            )
        }
        pass(
            BenchStepId.SELF_TESTS,
            "必須self-test ${manifest.tests.count { it.required }}件正常",
        )

        val telemetryResponse = exchange(
            RuntimeFrame(
                type = RuntimeMessageType.TELEMETRY,
                requestId = "bench-telemetry",
            )
        )
        if (telemetryResponse.type != RuntimeMessageType.TELEMETRY) {
            return fail(
                BenchStepId.TELEMETRY,
                "telemetry応答がありません。",
            )
        }

        val missingTelemetry = manifest.telemetryIds.filter {
            telemetryResponse.fields[it].isNullOrBlank()
        }
        if (missingTelemetry.isNotEmpty()) {
            return fail(
                BenchStepId.TELEMETRY,
                "telemetry不足: " + missingTelemetry.joinToString(", "),
                telemetryResponse.fields,
            )
        }
        pass(
            BenchStepId.TELEMETRY,
            "telemetry ${manifest.telemetryIds.size}項目確認",
        )

        val mutableSetting = manifest.settings.firstOrNull {
            it.mutableAtRuntime
        }
        if (mutableSetting == null) {
            pass(
                BenchStepId.SETTINGS_ROUNDTRIP,
                "runtime設定なし",
            )
            return BenchReport(
                projectId = manifest.projectId,
                steps = steps,
                telemetry = telemetryResponse.fields,
            )
        }

        val currentValue = client.getValue(
            requestId = "bench-get-" + mutableSetting.id,
            settingId = mutableSetting.id,
        ).getOrElse {
            return fail(
                BenchStepId.SETTINGS_ROUNDTRIP,
                it.message ?: "設定読出し失敗",
                telemetryResponse.fields,
                mutableSetting.id,
            )
        }

        client.setValue(
            requestId = "bench-set-" + mutableSetting.id,
            settingId = mutableSetting.id,
            value = currentValue.value,
        ).fold(
            onSuccess = { ack ->
                if (ack.appliedValue != currentValue.value) {
                    return fail(
                        BenchStepId.SETTINGS_ROUNDTRIP,
                        "設定値のread-back不一致",
                        telemetryResponse.fields,
                        mutableSetting.id,
                    )
                }
            },
            onFailure = {
                return fail(
                    BenchStepId.SETTINGS_ROUNDTRIP,
                    it.message ?: "設定書込み失敗",
                    telemetryResponse.fields,
                    mutableSetting.id,
                )
            },
        )

        pass(
            BenchStepId.SETTINGS_ROUNDTRIP,
            mutableSetting.id + " GET→同値SET正常",
        )

        return BenchReport(
            projectId = manifest.projectId,
            steps = steps,
            telemetry = telemetryResponse.fields,
            testedSettingId = mutableSetting.id,
        )
    }

    private fun exchange(frame: RuntimeFrame): RuntimeFrame {
        val response = transport.exchange(frame).getOrThrow()
        require(response.requestId == frame.requestId) {
            "Bench response requestId mismatch"
        }
        if (response.type == RuntimeMessageType.ERROR) {
            error(
                response.fields["code"].orEmpty() +
                    " " +
                    response.fields["message"].orEmpty()
            )
        }
        return response
    }
}
