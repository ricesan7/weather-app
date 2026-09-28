package com.aielectronics.editor

sealed interface AdvancedBuildResult {
    data class Success(
        val firmwareId: String,
        val log: String,
    ) : AdvancedBuildResult

    data class Failed(
        val log: String,
    ) : AdvancedBuildResult

    data class ServiceUnavailable(
        val message: String,
    ) : AdvancedBuildResult
}

interface AdvancedBuildService {
    fun build(workspace: AdvancedWorkspace): AdvancedBuildResult
}

class UnavailableAdvancedBuildService : AdvancedBuildService {
    override fun build(workspace: AdvancedWorkspace): AdvancedBuildResult =
        AdvancedBuildResult.ServiceUnavailable(
            "ファームウェアビルドサービスはまだ接続されていません。編集内容・差分・復元は利用できます。"
        )
}

data class WorkspaceValidation(
    val valid: Boolean,
    val messages: List<String>,
)

object AdvancedWorkspaceValidator {

    fun validate(workspace: AdvancedWorkspace): WorkspaceValidation {
        val messages = mutableListOf<String>()

        val source = workspace.files.firstOrNull { it.path == "src/project.cpp" }
        if (source == null) {
            messages += "src/project.cpp がありません。"
        } else if (source.workingContent.isBlank()) {
            messages += "src/project.cpp が空です。"
        }

        val manifest = workspace.files.firstOrNull {
            it.path == "runtime/project.manifest"
        }
        if (manifest == null) {
            messages += "Runtime Manifestがありません。"
        } else {
            val required = listOf(
                "meta\tversion\t",
                "meta\tproject\t",
                "meta\tboard\t",
            )
            required.forEach { prefix ->
                if (!manifest.workingContent.contains(prefix)) {
                    messages += "Manifest必須項目が不足しています: $prefix"
                }
            }
        }

        return WorkspaceValidation(
            valid = messages.isEmpty(),
            messages = messages,
        )
    }
}
