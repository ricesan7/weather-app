package com.aielectronics.runtime

import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.ValidationState

interface RuntimeInstaller {
    fun ensureInstalled(device: DeviceInfo, minimumVersion: String): Result<DeviceInfo>
}

class DefaultDeploymentPlanner : DeploymentPlanner {

    override fun plan(bundle: ReleaseBundle, device: DeviceInfo): Result<DeployPlan> = runCatching {
        require(bundle.validation.state != ValidationState.BLOCKED) {
            "Design is electrically blocked"
        }
        require(bundle.designIr.board.boardId == device.boardId) {
            "Connected board does not match the project"
        }

        val manifest = bundle.manifest
        if (manifest != null) {
            DeployPlan(
                device = device,
                strategy = DeployStrategy.MANIFEST_ONLY,
                requiresRuntimeInstall = !versionAtLeast(
                    device.runtimeVersion,
                    manifest.minimumRuntimeVersion,
                ),
                bundle = bundle,
            )
        } else {
            DeployPlan(
                device = device,
                strategy = DeployStrategy.GENERATED_FIRMWARE,
                requiresRuntimeInstall = false,
                bundle = bundle,
            )
        }
    }

    private fun versionAtLeast(actual: String?, required: String): Boolean {
        if (actual == null) return false
        val a = parseVersion(actual)
        val r = parseVersion(required)
        for (index in 0 until maxOf(a.size, r.size)) {
            val av = a.getOrElse(index) { 0 }
            val rv = r.getOrElse(index) { 0 }
            if (av != rv) return av > rv
        }
        return true
    }

    private fun parseVersion(value: String): List<Int> =
        value.substringBefore("-")
            .split(".")
            .map { token -> token.toIntOrNull() ?: 0 }
}

class DefaultDeployManager(
    private val clientFactory: (DeviceInfo) -> RuntimeClient,
    private val runtimeInstaller: RuntimeInstaller,
    private val manifestEncoder: ProjectManifestEncoder = CanonicalManifestEncoder(),
) : DeployManager {

    override fun deploy(
        plan: DeployPlan,
        onProgress: (DeployProgress) -> Unit,
    ): DeployResult {
        if (plan.strategy == DeployStrategy.GENERATED_FIRMWARE) {
            return DeployResult.Failed(
                state = DeployState.PLAN,
                userMessage = "この機能は現在のUniversal Runtimeでは未対応です。",
                technicalCode = "E_GENERATED_FIRMWARE_NOT_IMPLEMENTED",
            )
        }

        val manifest = plan.bundle.manifest
            ?: return DeployResult.Failed(
                DeployState.PLAN,
                "装置設定データを生成できませんでした。",
                "E_MANIFEST_MISSING",
            )

        fun progress(state: DeployState, percent: Int, message: String) {
            onProgress(DeployProgress(state, percent, message))
        }

        progress(DeployState.IDENTIFY, 5, "装置を確認しています")
        progress(DeployState.PREFLIGHT, 15, "安全確認をしています")

        var device = plan.device
        if (plan.requiresRuntimeInstall) {
            progress(DeployState.RUNTIME_CHECK, 30, "装置を準備しています")
            device = runtimeInstaller
                .ensureInstalled(device, manifest.minimumRuntimeVersion)
                .getOrElse {
                    return DeployResult.Failed(
                        DeployState.RUNTIME_CHECK,
                        "装置の準備に失敗しました。",
                        "E_RUNTIME_INSTALL",
                    )
                }
        }

        val client = clientFactory(device)

        progress(DeployState.TRANSFER, 55, "装置へ設定しています")
        client.deployManifest(
            requestId = "deploy",
            manifestVersion = manifest.version,
            payload = manifestEncoder.encode(manifest),
            projectId = manifest.projectId,
        ).getOrElse {
            return DeployResult.Failed(
                DeployState.TRANSFER,
                "装置への設定送信に失敗しました。",
                "E_DEPLOY_MANIFEST",
            )
        }

        progress(DeployState.VERIFY, 75, "設定を確認しています")
        client.verifyProject("verify", manifest.projectId).getOrElse {
            return DeployResult.Failed(
                DeployState.VERIFY,
                "装置の設定確認に失敗しました。",
                "E_VERIFY_PROJECT",
            )
        }

        progress(DeployState.SELF_TEST, 90, "動作チェック中")
        plan.bundle.testPlan.tests.filter { it.required }.forEach { test ->
            client.runTest("test-" + test.id, test.id).getOrElse {
                return DeployResult.Failed(
                    DeployState.SELF_TEST,
                    test.name + "で確認が必要です。",
                    "E_SELF_TEST_" + test.id,
                )
            }
        }

        progress(DeployState.READY, 100, "完成しました")
        return DeployResult.Success(device)
    }
}
