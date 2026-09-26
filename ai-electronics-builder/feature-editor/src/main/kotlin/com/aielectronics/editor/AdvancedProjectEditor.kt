package com.aielectronics.editor

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aielectronics.core.model.ReleaseBundle

private enum class EditorPane {
    FILES,
    EDIT,
    DIFF,
    LOG,
}

@Composable
fun AdvancedProjectEditor(
    bundle: ReleaseBundle,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val factory = remember(bundle.designIr.project.id) {
        AdvancedEditorViewModel.Factory(bundle)
    }
    val editorViewModel: AdvancedEditorViewModel = viewModel(
        key = "advanced-editor-" + bundle.designIr.project.id,
        factory = factory,
    )
    val state by editorViewModel.state.collectAsState()
    var pane by remember { mutableStateOf(EditorPane.EDIT) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(onClick = onBack) {
                Text("戻る")
            }
            Column(modifier = Modifier.fillMaxWidth(0.55f)) {
                Text(
                    "上級者モード",
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    "編集ファイル " + state.workspace.modifiedFileCount + "件",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(onClick = editorViewModel::validateWorkspace) {
                Text("検証")
            }
            Button(onClick = editorViewModel::build) {
                Text("ビルド")
            }
        }

        Surface(
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                "生成版は保護されています。コードやManifestを変更しても、電気的安全性が自動的に保証されるわけではありません。",
                modifier = Modifier.padding(10.dp),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        PaneSelector(
            selected = pane,
            onSelect = { pane = it },
        )

        when (pane) {
            EditorPane.FILES -> FileTreePane(
                workspace = state.workspace,
                onSelect = {
                    editorViewModel.select(it)
                    pane = EditorPane.EDIT
                },
                onRestoreAll = editorViewModel::restoreAll,
            )

            EditorPane.EDIT -> EditPane(
                state = state,
                onEdit = editorViewModel::edit,
                onSearch = editorViewModel::setSearch,
                onReplacement = editorViewModel::setReplacement,
                onReplaceNext = editorViewModel::replaceNext,
                onReplaceAll = editorViewModel::replaceAll,
                onRestore = editorViewModel::restoreSelected,
            )

            EditorPane.DIFF -> DiffPane(
                file = state.workspace.selectedFile,
                lines = editorViewModel.diff(),
            )

            EditorPane.LOG -> LogPane(state.workspace.logs)
        }
    }
}

@Composable
private fun PaneSelector(
    selected: EditorPane,
    onSelect: (EditorPane) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf(
            EditorPane.FILES to "ファイル",
            EditorPane.EDIT to "編集",
            EditorPane.DIFF to "差分",
            EditorPane.LOG to "ログ",
        ).forEach { item ->
            if (selected == item.first) {
                Button(onClick = { onSelect(item.first) }) {
                    Text(item.second)
                }
            } else {
                OutlinedButton(onClick = { onSelect(item.first) }) {
                    Text(item.second)
                }
            }
        }
    }
}

@Composable
private fun FileTreePane(
    workspace: AdvancedWorkspace,
    onSelect: (String) -> Unit,
    onRestoreAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                "プロジェクトファイル",
                style = MaterialTheme.typography.titleMedium,
            )
            OutlinedButton(
                onClick = onRestoreAll,
                enabled = workspace.modifiedFileCount > 0,
            ) {
                Text("すべて生成版へ戻す")
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(
                items = workspace.files,
                key = { it.path },
            ) { file ->
                Card(
                    onClick = { onSelect(file.path) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text =
                                (if (file.modified) "● " else "") +
                                    file.path,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = when {
                                file.readOnly -> "参照専用"
                                file.modified -> "編集済み"
                                else -> "生成版と同じ"
                            },
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EditPane(
    state: AdvancedEditorState,
    onEdit: (String) -> Unit,
    onSearch: (String) -> Unit,
    onReplacement: (String) -> Unit,
    onReplaceNext: () -> Unit,
    onReplaceAll: () -> Unit,
    onRestore: () -> Unit,
) {
    val file = state.workspace.selectedFile ?: return

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    file.path,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    if (file.readOnly) "参照専用" else if (file.modified) "編集済み" else "生成版",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            OutlinedButton(
                onClick = onRestore,
                enabled = file.modified && !file.readOnly,
            ) {
                Text("生成版へ戻す")
            }
        }

        if (!file.readOnly) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = state.searchText,
                    onValueChange = onSearch,
                    label = { Text("検索") },
                    modifier = Modifier.fillMaxWidth(0.48f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.replaceText,
                    onValueChange = onReplacement,
                    label = { Text("置換") },
                    modifier = Modifier.fillMaxWidth(0.48f),
                    singleLine = true,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onReplaceNext,
                    enabled = state.searchText.isNotEmpty(),
                ) {
                    Text("次を置換")
                }
                OutlinedButton(
                    onClick = onReplaceAll,
                    enabled = state.searchText.isNotEmpty(),
                ) {
                    Text("すべて置換")
                }
            }
        }

        Surface(
            tonalElevation = 1.dp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 360.dp, max = 560.dp),
        ) {
            BasicTextField(
                value = file.workingContent,
                onValueChange = { if (!file.readOnly) onEdit(it) },
                readOnly = file.readOnly,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                visualTransformation = CodeSyntaxVisualTransformation(
                    file.language
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .verticalScroll(rememberScrollState()),
            )
        }
    }
}

@Composable
private fun DiffPane(
    file: WorkspaceFile?,
    lines: List<DiffLine>,
) {
    if (file == null) return

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            file.path + " — 生成版との差分",
            style = MaterialTheme.typography.titleMedium,
        )

        if (!file.modified) {
            Text("差分はありません。")
            return
        }

        LazyColumn {
            items(lines) { line ->
                val prefix = when (line.kind) {
                    DiffKind.SAME -> "  "
                    DiffKind.ADDED -> "+ "
                    DiffKind.REMOVED -> "- "
                }
                val background = when (line.kind) {
                    DiffKind.SAME -> Color.Transparent
                    DiffKind.ADDED -> Color(0x1A2E7D32)
                    DiffKind.REMOVED -> Color(0x1AD32F2F)
                }

                Text(
                    text = prefix + line.text,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(background)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun LogPane(logs: List<EditorLogEntry>) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "ビルド・検証ログ",
            style = MaterialTheme.typography.titleMedium,
        )
        HorizontalDivider()
        LazyColumn {
            items(logs.reversed()) { entry ->
                Text(
                    text =
                        "#" + entry.sequence + " [" + entry.level.name + "] " +
                            entry.message,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
        }
    }
}

private class CodeSyntaxVisualTransformation(
    private val language: EditorLanguage,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        if (
            language != EditorLanguage.CPP &&
            language != EditorLanguage.HEADER
        ) {
            return TransformedText(text, OffsetMapping.Identity)
        }

        val builder = AnnotatedString.Builder(text)
        val source = text.text

        Regex(
            """\b(class|struct|namespace|void|int|double|float|bool|const|constexpr|return|if|else|for|while|auto|include|define)\b"""
        ).findAll(source).forEach { match ->
            builder.addStyle(
                SpanStyle(color = Color(0xFF6A1B9A)),
                match.range.first,
                match.range.last + 1,
            )
        }

        Regex("""//.*$""", setOf(RegexOption.MULTILINE))
            .findAll(source)
            .forEach { match ->
                builder.addStyle(
                    SpanStyle(color = Color(0xFF607D8B)),
                    match.range.first,
                    match.range.last + 1,
                )
            }

        Regex(""""(?:\\.|[^"\\])*"""")
            .findAll(source)
            .forEach { match ->
                builder.addStyle(
                    SpanStyle(color = Color(0xFF2E7D32)),
                    match.range.first,
                    match.range.last + 1,
                )
            }

        return TransformedText(
            builder.toAnnotatedString(),
            OffsetMapping.Identity,
        )
    }
}
