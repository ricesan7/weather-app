package com.aielectronics.runtime

class RuntimeClient(
    private val transport: RuntimeTransport,
) {

    fun deployManifest(
        requestId: String,
        manifestVersion: String,
        payload: String,
        projectId: String,
    ): Result<Unit> = runCatching {
        val response = transport.exchange(
            RuntimeFrame(
                type = RuntimeMessageType.DEPLOY_MANIFEST,
                requestId = requestId,
                fields = mapOf(
                    "manifest_version" to manifestVersion,
                    "project_id" to projectId,
                    "payload" to payload,
                ),
            )
        ).getOrThrow()

        require(response.type == RuntimeMessageType.DEPLOY_RESULT) {
            "Expected DEPLOY_RESULT"
        }
        require(response.fields["ok"] == "true") {
            response.fields["message"] ?: "Manifest deployment rejected"
        }
    }

    fun verifyProject(requestId: String, projectId: String): Result<Unit> = runCatching {
        val response = transport.exchange(
            RuntimeFrame(
                type = RuntimeMessageType.VERIFY_PROJECT,
                requestId = requestId,
                fields = mapOf("project_id" to projectId),
            )
        ).getOrThrow()

        require(response.type == RuntimeMessageType.VERIFY_RESULT) {
            "Expected VERIFY_RESULT"
        }
        require(response.fields["ok"] == "true") {
            response.fields["message"] ?: "Project verification failed"
        }
    }

    fun runTest(requestId: String, testId: String): Result<Unit> = runCatching {
        val response = transport.exchange(
            RuntimeFrame(
                type = RuntimeMessageType.RUN_TEST,
                requestId = requestId,
                fields = mapOf("test_id" to testId),
            )
        ).getOrThrow()

        require(response.type == RuntimeMessageType.TEST_RESULT) {
            "Expected TEST_RESULT"
        }
        require(response.fields["passed"] == "true") {
            response.fields["message"] ?: "Self-test failed"
        }
    }

    fun setValue(requestId: String, settingId: String, value: String): Result<SettingAck> =
        runCatching {
            val response = transport.exchange(
                RuntimeFrame(
                    type = RuntimeMessageType.SET_VALUE,
                    requestId = requestId,
                    fields = mapOf(
                        "setting_id" to settingId,
                        "value" to value,
                    ),
                )
            ).getOrThrow()

            require(response.type == RuntimeMessageType.VALUE) { "Expected VALUE" }
            require(response.fields["setting_id"] == settingId) { "Setting ID mismatch" }

            SettingAck(
                settingId = settingId,
                appliedValue = response.fields["value"] ?: error("Applied value missing"),
                persisted = response.fields["persisted"] == "true",
            )
        }

    fun getValue(requestId: String, settingId: String): Result<RuntimeSettingValue> =
        runCatching {
            val response = transport.exchange(
                RuntimeFrame(
                    type = RuntimeMessageType.GET_VALUE,
                    requestId = requestId,
                    fields = mapOf("setting_id" to settingId),
                )
            ).getOrThrow()

            require(response.type == RuntimeMessageType.VALUE) { "Expected VALUE" }
            RuntimeSettingValue(
                settingId = response.fields["setting_id"] ?: error("Setting ID missing"),
                value = response.fields["value"] ?: error("Setting value missing"),
            )
        }
}
