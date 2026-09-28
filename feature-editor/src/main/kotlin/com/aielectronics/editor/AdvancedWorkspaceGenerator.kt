package com.aielectronics.editor

import com.aielectronics.core.model.Action
import com.aielectronics.core.model.Expression
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.runtime.CanonicalManifestEncoder
import com.aielectronics.runtime.ProjectManifestEncoder

class AdvancedWorkspaceGenerator(
    private val manifestEncoder: ProjectManifestEncoder = CanonicalManifestEncoder(),
) {

    fun generate(bundle: ReleaseBundle): AdvancedWorkspace {
        val manifest = bundle.manifest
            ?: error("Advanced editor requires a generated ProjectManifest")

        val files = listOf(
            WorkspaceFile(
                path = "src/project.cpp",
                language = EditorLanguage.CPP,
                baselineContent = generatedProjectSource(bundle),
            ),
            WorkspaceFile(
                path = "include/project_config.hpp",
                language = EditorLanguage.HEADER,
                baselineContent = generatedProjectHeader(bundle),
            ),
            WorkspaceFile(
                path = "runtime/project.manifest",
                language = EditorLanguage.MANIFEST,
                baselineContent = manifestEncoder.encode(manifest),
            ),
            WorkspaceFile(
                path = "hardware/netlist.txt",
                language = EditorLanguage.TEXT,
                baselineContent = generatedNetlist(bundle),
                readOnly = true,
            ),
            WorkspaceFile(
                path = "README.md",
                language = EditorLanguage.MARKDOWN,
                baselineContent = generatedReadme(bundle),
                readOnly = true,
            ),
        )

        return AdvancedWorkspace(
            projectId = bundle.designIr.project.id,
            files = files,
            selectedPath = files.first().path,
            logs = listOf(
                EditorLogEntry(
                    sequence = 1,
                    level = LogLevel.INFO,
                    message = "検証済み生成版から上級者ワークスペースを作成しました。",
                )
            ),
        )
    }

    private fun generatedProjectSource(bundle: ReleaseBundle): String {
        val rules = bundle.designIr.behavior.rules.joinToString("\n") { rule ->
            "// " + rule.id + ": " +
                raw(rule.condition) + " -> " +
                rule.actions.joinToString(", ") { actionText(it) }
        }

        val settings = bundle.designIr.settings.joinToString("\n") { setting ->
            "// " + setting.id + " = " + setting.defaultValue +
                if (setting.mutableAtRuntime) " (runtime mutable)" else ""
        }

        return """
#include "project_config.hpp"

// Generated advanced-mode source baseline.
// This file is separate from the beginner Universal Runtime path.
// Keep hardware safety constraints aligned with Design IR / CircuitGraph.

namespace project {

void on_setup() {
    // Add custom startup behavior here.
}

void on_tick() {
    // Add custom runtime behavior here.
}

// Generated behavior reference:
$rules

// Generated settings reference:
$settings

} // namespace project
""".trim()
    }

    private fun generatedProjectHeader(bundle: ReleaseBundle): String {
        val settings = bundle.designIr.settings.joinToString("\n") { setting ->
            val name = setting.id
                .uppercase()
                .replace(Regex("[^A-Z0-9_]"), "_")
            "#define AIE_SETTING_" + name + " " + quoted(setting.defaultValue)
        }

        return """
#pragma once

#define AIE_PROJECT_ID ${quoted(bundle.designIr.project.id)}
#define AIE_BOARD_ID ${quoted(bundle.designIr.board.boardId)}

$settings
""".trim()
    }

    private fun generatedNetlist(bundle: ReleaseBundle): String =
        buildString {
            appendLine("Project: " + bundle.designIr.project.id)
            appendLine("Board: " + bundle.designIr.board.boardId)
            appendLine()
            appendLine("Authoritative connections:")
            bundle.circuitGraph.connections.forEach { connection ->
                appendLine(
                    connection.id + "\t" +
                        connection.from.entityId + ":" + connection.from.pinId +
                        " -> " +
                        connection.to.entityId + ":" + connection.to.pinId +
                        "\t" + connection.netType.name
                )
            }
        }.trimEnd()

    private fun generatedReadme(bundle: ReleaseBundle): String =
        """
# ${bundle.designIr.project.name}

Goal: ${bundle.designIr.project.goal}

## Advanced mode

- src/project.cpp: editable custom logic shell
- include/project_config.hpp: editable generated constants
- runtime/project.manifest: editable Universal Runtime manifest
- hardware/netlist.txt: read-only authoritative wiring reference
- README.md: read-only workspace guide

The original generated baseline is immutable inside the editor. Use Diff before applying changes and Restore to return to the validated generated version.

Changing code or manifest does not automatically make a hardware change safe. Pin/power/component changes must be revalidated before deployment.
""".trim()

    private fun quoted(value: String): String =
        "\"" + value.replace("\"", "\\\"") + "\""

    private fun raw(expression: Expression): String = when (expression) {
        is Expression.Raw -> expression.expression
    }

    private fun actionText(action: Action): String = when (action) {
        is Action.SetOutput -> "set " + action.outputId + "=" + action.value
        is Action.RaiseEvent -> "event " + action.eventId
        is Action.SetState -> "state " + action.stateId
    }
}
