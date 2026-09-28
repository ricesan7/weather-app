package com.aielectronics.editor

enum class EditorLanguage {
    CPP,
    HEADER,
    MANIFEST,
    TEXT,
    MARKDOWN,
}

data class WorkspaceFile(
    val path: String,
    val language: EditorLanguage,
    val baselineContent: String,
    val workingContent: String = baselineContent,
    val readOnly: Boolean = false,
) {
    val modified: Boolean get() = baselineContent != workingContent
}

data class EditorLogEntry(
    val sequence: Long,
    val level: LogLevel,
    val message: String,
)

enum class LogLevel {
    INFO,
    WARNING,
    ERROR,
}

data class AdvancedWorkspace(
    val projectId: String,
    val files: List<WorkspaceFile>,
    val selectedPath: String,
    val logs: List<EditorLogEntry> = emptyList(),
) {
    val selectedFile: WorkspaceFile?
        get() = files.firstOrNull { it.path == selectedPath }

    val modifiedFileCount: Int
        get() = files.count { it.modified }
}

enum class DiffKind {
    SAME,
    ADDED,
    REMOVED,
}

data class DiffLine(
    val kind: DiffKind,
    val lineNumber: Int?,
    val text: String,
)

class AdvancedWorkspaceController(
    initial: AdvancedWorkspace,
) {
    private var current = initial
    private var logSequence = initial.logs.maxOfOrNull { it.sequence } ?: 0L

    fun snapshot(): AdvancedWorkspace = current

    fun select(path: String): AdvancedWorkspace {
        require(current.files.any { it.path == path }) { "Unknown file: $path" }
        current = current.copy(selectedPath = path)
        return current
    }

    fun editSelected(content: String): AdvancedWorkspace {
        val selected = current.selectedFile ?: return current
        require(!selected.readOnly) { "File is read-only" }

        current = current.copy(
            files = current.files.map { file ->
                if (file.path == selected.path) {
                    file.copy(workingContent = content)
                } else {
                    file
                }
            }
        )
        return current
    }

    fun replaceInSelected(
        search: String,
        replacement: String,
        replaceAll: Boolean,
    ): AdvancedWorkspace {
        if (search.isEmpty()) return current
        val selected = current.selectedFile ?: return current
        require(!selected.readOnly) { "File is read-only" }

        val updated = if (replaceAll) {
            selected.workingContent.replace(search, replacement)
        } else {
            selected.workingContent.replaceFirst(search, replacement)
        }
        return editSelected(updated)
    }

    fun restoreSelected(): AdvancedWorkspace {
        val selected = current.selectedFile ?: return current
        if (selected.readOnly) return current

        current = current.copy(
            files = current.files.map { file ->
                if (file.path == selected.path) {
                    file.copy(workingContent = file.baselineContent)
                } else {
                    file
                }
            }
        )
        appendLog(LogLevel.INFO, selected.path + " を生成版へ戻しました。")
        return current
    }

    fun restoreAll(): AdvancedWorkspace {
        current = current.copy(
            files = current.files.map { file ->
                if (file.readOnly) file
                else file.copy(workingContent = file.baselineContent)
            }
        )
        appendLog(LogLevel.INFO, "すべての編集を検証済み生成版へ戻しました。")
        return current
    }

    fun diffSelected(): List<DiffLine> {
        val selected = current.selectedFile ?: return emptyList()
        return lineDiff(
            baseline = selected.baselineContent,
            working = selected.workingContent,
        )
    }

    fun appendLog(
        level: LogLevel,
        message: String,
    ): AdvancedWorkspace {
        logSequence += 1
        current = current.copy(
            logs = (
                current.logs +
                    EditorLogEntry(logSequence, level, message)
            ).takeLast(200)
        )
        return current
    }

    private fun lineDiff(
        baseline: String,
        working: String,
    ): List<DiffLine> {
        val before = baseline.lines()
        val after = working.lines()
        val count = maxOf(before.size, after.size)
        val result = mutableListOf<DiffLine>()

        for (index in 0 until count) {
            val old = before.getOrNull(index)
            val new = after.getOrNull(index)

            when {
                old == new && old != null ->
                    result += DiffLine(
                        kind = DiffKind.SAME,
                        lineNumber = index + 1,
                        text = old,
                    )

                old != null && new != null -> {
                    result += DiffLine(
                        kind = DiffKind.REMOVED,
                        lineNumber = index + 1,
                        text = old,
                    )
                    result += DiffLine(
                        kind = DiffKind.ADDED,
                        lineNumber = index + 1,
                        text = new,
                    )
                }

                old != null ->
                    result += DiffLine(
                        kind = DiffKind.REMOVED,
                        lineNumber = index + 1,
                        text = old,
                    )

                new != null ->
                    result += DiffLine(
                        kind = DiffKind.ADDED,
                        lineNumber = index + 1,
                        text = new,
                    )
            }
        }
        return result
    }
}
