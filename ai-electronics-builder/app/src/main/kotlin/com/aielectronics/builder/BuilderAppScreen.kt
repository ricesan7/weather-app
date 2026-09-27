package com.aielectronics.builder

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.aielectronics.application.DesignExplanationBuilder
import com.aielectronics.application.SavedGraphNodePosition
import com.aielectronics.assembly.GuidedBuildSnapshot
import com.aielectronics.assembly.GuidedBuildStateMachine
import com.aielectronics.bench.BenchGateScreen
import com.aielectronics.control.RuntimeControlDashboard
import com.aielectronics.editor.AdvancedProjectEditor
import com.aielectronics.core.model.DiagramSpec
import com.aielectronics.core.model.NetType
import com.aielectronics.core.model.ProjectGraph
import com.aielectronics.core.model.ProjectGraphDomain
import com.aielectronics.core.model.ProjectGraphNodeKind
import com.aielectronics.parts.GoldenEngineeringCatalog

@Composable
fun BuilderAppScreen(
    state: BuilderAppState,
    onGoalChange: (String) -> Unit,
    onAdditionalRequestChange: (String) -> Unit,
    onApplyAdditionalRequest: () -> Unit,
    onStartDesign: () -> Unit,
    onAnswerQuestion: (String, String) -> Unit,
    onOpen: (AppScreen) -> Unit,
    onConnect: () -> Unit,
    onDeploy: () -> Unit,
    onBridgePairingCodeChange: (String) -> Unit,
    onPairBase44: () -> Unit,
    onReceiveBase44Design: () -> Unit,
    onBuildProgress: (Set<String>, Int) -> Unit,
    onResumeProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onOpenBuildStep: (Int) -> Unit,
    onGraphNodeSelect: (String?) -> Unit,
    onGraphNodeMove: (String, SavedGraphNodePosition) -> Unit,
    onGraphNodeMoveFinished: () -> Unit,
    onGraphLayoutUndo: () -> Unit,
    onGraphLayoutRedo: () -> Unit,
    onGraphAddElement: (String) -> Unit,
    onGraphChangeNode: (String, String) -> Unit,
    onGraphDeleteNode: (String) -> Unit,
    onClearError: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AppTitle(state)

        if (
            state.projectId != null &&
            state.screen != AppScreen.HOME &&
            state.screen != AppScreen.REVISION
        ) {
            OutlinedButton(
                onClick = { onOpen(AppScreen.REVISION) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("追加要望を伝える")
            }
        }

        state.error?.let { message ->
            ErrorCard(message, onClearError)
        }

        when (state.screen) {
            AppScreen.HOME -> HomeScreen(
                state = state,
                onGoalChange = onGoalChange,
                onStartDesign = onStartDesign,
                onAnswerQuestion = onAnswerQuestion,
                onBridgePairingCodeChange = onBridgePairingCodeChange,
                onReceiveBase44Design = onReceiveBase44Design,
                onResumeProject = onResumeProject,
                onDeleteProject = onDeleteProject,
                onNewProject = onNewProject,
            )
            AppScreen.DESIGN -> DesignScreen(state, onOpen)
            AppScreen.GRAPH -> ProjectGraphScreen(
                state = state,
                onOpen = onOpen,
                onNodeSelect = onGraphNodeSelect,
                onNodeMove = onGraphNodeMove,
                onNodeMoveFinished = onGraphNodeMoveFinished,
                onLayoutUndo = onGraphLayoutUndo,
                onLayoutRedo = onGraphLayoutRedo,
                onAddElement = onGraphAddElement,
                onChangeNode = onGraphChangeNode,
                onDeleteNode = onGraphDeleteNode,
            )
            AppScreen.PARTS -> PartsScreen(state, onOpen)
            AppScreen.WIRING -> WiringScreen(state, onOpen)
            AppScreen.BUILD -> BuildScreen(
                state = state,
                onOpen = onOpen,
                onBuildProgress = onBuildProgress,
            )
            AppScreen.CONNECT -> ConnectScreen(
                state = state,
                onConnect = onConnect,
                onDeploy = onDeploy,
                onBridgePairingCodeChange = onBridgePairingCodeChange,
                onPairBase44 = onPairBase44,
                onOpen = onOpen,
            )
            AppScreen.CONTROL -> ControlScreen(
                state = state,
                onOpen = onOpen,
                onOpenBuildStep = onOpenBuildStep,
            )
            AppScreen.REVISION -> RevisionScreen(
                state = state,
                onAdditionalRequestChange = onAdditionalRequestChange,
                onApplyAdditionalRequest = onApplyAdditionalRequest,
                onOpen = onOpen,
            )
            AppScreen.EDITOR -> AdvancedEditorScreen(
                state = state,
                onOpen = onOpen,
            )
            AppScreen.BENCH -> BenchScreen(
                state = state,
                onOpen = onOpen,
            )
        }
    }
}

@Composable
private fun AppTitle(state: BuilderAppState) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "AI Electronics Builder",
            style = MaterialTheme.typography.headlineSmall,
        )
        state.projectTitle?.let { title ->
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
            )
        }
        Text(
            text = when (state.screen) {
                AppScreen.HOME -> "作りたいものを話す"
                AppScreen.DESIGN -> "設計"
                AppScreen.GRAPH -> "Visual Project"
                AppScreen.PARTS -> "部品"
                AppScreen.WIRING -> "配線"
                AppScreen.BUILD -> "組立"
                AppScreen.CONNECT -> "装置へ設定"
                AppScreen.CONTROL -> "操作・診断"
                AppScreen.REVISION -> "追加要望"
                AppScreen.EDITOR -> "上級者モード"
                AppScreen.BENCH -> "実機ベンチE2E"
            },
            style = MaterialTheme.typography.titleMedium,
        )
    }
}

@Composable
private fun ErrorCard(message: String, onDismiss: () -> Unit) {
    Surface(
        tonalElevation = 4.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onDismiss) {
                Text("閉じる")
            }
        }
    }
}

@Composable
private fun HomeScreen(
    state: BuilderAppState,
    onGoalChange: (String) -> Unit,
    onStartDesign: () -> Unit,
    onAnswerQuestion: (String, String) -> Unit,
    onBridgePairingCodeChange: (String) -> Unit,
    onReceiveBase44Design: () -> Unit,
    onResumeProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onNewProject: () -> Unit,
) {
    var answer by remember(state.pendingQuestions.firstOrNull()?.slotId) {
        mutableStateOf("")
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "Base44から設計を受け取る",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "CircuitFlowで「仕様を確定して実機設計へ送る」を押した時に表示される接続コードを入力します。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = state.bridgePairingCode,
                        onValueChange = onBridgePairingCodeChange,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("接続コード") },
                        singleLine = true,
                        enabled = !state.busy,
                    )
                    Button(
                        onClick = onReceiveBase44Design,
                        enabled =
                            state.bridgePairingCode.isNotBlank() &&
                                !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            if (
                                state.busy &&
                                state.base44HandoffStatus == "receiving"
                            ) {
                                "確定仕様を受信中…"
                            } else {
                                "Base44の確定仕様から自動設計"
                            }
                        )
                    }
                    if (state.base44HandoffMessage.isNotBlank()) {
                        Text(
                            state.base44HandoffMessage,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }
        item {
            Text(
                "または、このアプリから直接作りたいものを入力できます。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            OutlinedTextField(
                value = state.goalText,
                onValueChange = onGoalChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                label = { Text("何を作りたいですか？") },
                enabled = !state.busy,
            )
        }
        item {
            Button(
                onClick = onStartDesign,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "設計中…" else "設計する")
            }
        }


        if (state.projectId != null) {
            item {
                OutlinedButton(
                    onClick = onNewProject,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("新しいプロジェクト")
                }
            }
        }

        if (state.savedProjects.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text(
                    "保存したプロジェクト",
                    style = MaterialTheme.typography.titleMedium,
                )
            }

            items(
                items = state.savedProjects,
                key = { it.id },
            ) { project ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            project.title,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "組立済み ${project.completedConnectionCount}本" +
                                if (project.deployed) " / 装置設定済み" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Button(
                                onClick = { onResumeProject(project.id) },
                                enabled = !state.busy,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("再開")
                            }
                            OutlinedButton(
                                onClick = { onDeleteProject(project.id) },
                                enabled = !state.busy,
                            ) {
                                Text("削除")
                            }
                        }
                    }
                }
            }
        }

        state.pendingQuestions.firstOrNull()?.let { question ->
            item {
                HorizontalDivider()
                Text(
                    text = question.userQuestion,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            item {
                OutlinedTextField(
                    value = answer,
                    onValueChange = { answer = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("回答") },
                )
            }
            item {
                Button(
                    onClick = {
                        onAnswerQuestion(question.slotId, answer)
                        answer = ""
                    },
                    enabled = answer.isNotBlank() && !state.busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("回答して設計を続ける")
                }
            }
        }
    }
}

@Composable
private fun DesignScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
) {
    val bundle = state.bundle ?: return
    val catalog = GoldenEngineeringCatalog
    val board = catalog.board(bundle.designIr.board.boardId)

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            InfoCard("作るもの", bundle.designIr.project.goal)
        }
        item {
            InfoCard(
                "マイコン",
                board?.displayName ?: bundle.designIr.board.boardId,
            )
        }
        item {
            InfoCard("安全確認", bundle.validation.state.name)
        }
        state.base44HandoffRevision?.let { revision ->
            item {
                InfoCard(
                    "Base44 Design Handoff",
                    "revision " + revision + " / " +
                        if (state.base44HandoffStatus == "compiled") {
                            "設計・安全検証完了"
                        } else {
                            state.base44HandoffStatus
                        },
                )
            }
        }
        state.lastSavedAtEpochMs?.let {
            item {
                InfoCard("保存", "このプロジェクトは端末に自動保存されています")
            }
        }
        if (state.revisionStatusMessage.isNotBlank()) {
            item {
                InfoCard("更新", state.revisionStatusMessage)
            }
        }
        item {
            InfoCard(
                "自動生成",
                "部品 ${bundle.designIr.components.size}点 / " +
                    "配線 ${bundle.circuitGraph.connections.size}本 / " +
                    "操作画面 ${bundle.uiSpec.pages.size}ページ",
            )
        }
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "この設計になった理由",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    DesignExplanationBuilder.explain(bundle).forEach { reason ->
                        Text("・" + reason)
                    }
                }
            }
        }
        item {
            Button(
                onClick = { onOpen(AppScreen.GRAPH) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Visual Projectを見る")
            }
        }
        item {
            OutlinedButton(
                onClick = { onOpen(AppScreen.PARTS) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("必要な部品を見る")
            }
        }
        item {
            OutlinedButton(
                onClick = { onOpen(AppScreen.EDITOR) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("上級者モードでコードを見る")
            }
        }
        item {
            OutlinedButton(
                onClick = { onOpen(AppScreen.HOME) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("作り直す")
            }
        }
    }
}

@Composable
private fun ProjectGraphScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
    onNodeSelect: (String?) -> Unit,
    onNodeMove: (String, SavedGraphNodePosition) -> Unit,
    onNodeMoveFinished: () -> Unit,
    onLayoutUndo: () -> Unit,
    onLayoutRedo: () -> Unit,
    onAddElement: (String) -> Unit,
    onChangeNode: (String, String) -> Unit,
    onDeleteNode: (String) -> Unit,
) {
    val bundle = state.bundle ?: return
    val graph = bundle.projectGraph
    val selectedNode = graph.nodes.firstOrNull {
        it.id == state.selectedGraphNodeId
    }

    var addText by remember(graph.schemaVersion) {
        mutableStateOf("")
    }
    var changeText by remember(state.selectedGraphNodeId) {
        mutableStateOf("")
    }
    var paletteQuery by remember {
        mutableStateOf("")
    }
    val paletteComponents = remember(paletteQuery) {
        val query = paletteQuery.trim().lowercase()
        GoldenEngineeringCatalog.components()
            .filter { it.designReady && it.providesCapabilities.isNotEmpty() }
            .filter { component ->
                query.isBlank() ||
                    component.displayName.lowercase().contains(query) ||
                    component.componentId.lowercase().contains(query) ||
                    component.defaultRole.lowercase().contains(query) ||
                    component.providesCapabilities.any { capability ->
                        visualCapabilityLabel(capability.value)
                            .lowercase()
                            .contains(query)
                    }
            }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            InfoCard(
                "Visual Project Editor",
                "ノード " + graph.nodes.size + "個 / 接続 " + graph.edges.size + "本",
            )
        }
        item {
            Text(
                "ノードをタップして選択し、ドラッグして配置を変更できます。" +
                    "部品や機能の追加・変更・削除は再設計と安全検証を通して反映します。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            ProjectGraphCanvas(
                graph = graph,
                persistedPositions = state.graphNodePositions,
                selectedNodeId = state.selectedGraphNodeId,
                onNodeSelect = onNodeSelect,
                onNodeMove = onNodeMove,
                onNodeMoveFinished = onNodeMoveFinished,
            )
        }
        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                OutlinedButton(
                    onClick = onLayoutUndo,
                    enabled =
                        state.graphLayoutUndoStack.isNotEmpty() &&
                            !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("元に戻す")
                }
                OutlinedButton(
                    onClick = onLayoutRedo,
                    enabled =
                        state.graphLayoutRedoStack.isNotEmpty() &&
                            !state.busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("やり直す")
                }
            }
        }

        selectedNode?.let { node ->
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            node.label,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            node.domain.name + " / " + node.kind.name,
                            style = MaterialTheme.typography.labelMedium,
                        )
                        node.metadata
                            .filterValues { it.isNotBlank() }
                            .entries
                            .take(6)
                            .forEach { (key, value) ->
                                Text(
                                    key + ": " + value,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }

                        OutlinedTextField(
                            value = changeText,
                            onValueChange = { changeText = it },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            label = { Text("この要素をどう変更しますか？") },
                            enabled = !state.busy,
                        )
                        Button(
                            onClick = {
                                onChangeNode(node.id, changeText)
                                changeText = ""
                            },
                            enabled = changeText.isNotBlank() && !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.busy) "再設計中…" else "変更を設計へ反映")
                        }

                        if (
                            node.kind != ProjectGraphNodeKind.BOARD &&
                            node.kind != ProjectGraphNodeKind.RUNTIME
                        ) {
                            OutlinedButton(
                                onClick = { onDeleteNode(node.id) },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("この要素を削除")
                            }
                        }
                    }
                }
            }
        }

        if (paletteComponents.isNotEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "検証済み部品パレット",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "設計Readyの部品だけを表示しています。必要なドライバや電源部品は再設計時に自動選定します。",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedTextField(
                            value = paletteQuery,
                            onValueChange = { paletteQuery = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("部品・機能を検索") },
                            enabled = !state.busy,
                        )
                        if (paletteComponents.isEmpty()) {
                            Text(
                                "一致する検証済み部品はありません。",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        paletteComponents.forEach { component ->
                            OutlinedButton(
                                onClick = {
                                    val capabilities = component.providesCapabilities
                                        .joinToString("・") { capability ->
                                            visualCapabilityLabel(capability.value)
                                        }
                                    onAddElement(
                                        component.displayName +
                                            " (" + component.componentId + ") を追加し、" +
                                            capabilities +
                                            "ができるようにしてください。" +
                                            "この検証済み型番を優先して設計してください。"
                                    )
                                },
                                enabled = !state.busy,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(component.displayName)
                            }
                        }
                    }
                }
            }
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "要素を追加",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "例：照度センサーを追加 / モーターを追加 / スマホに設定画面を追加",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedTextField(
                        value = addText,
                        onValueChange = { addText = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2,
                        label = { Text("追加したい部品・機能・画面") },
                        enabled = !state.busy,
                    )
                    Button(
                        onClick = {
                            onAddElement(addText)
                            addText = ""
                        },
                        enabled = addText.isNotBlank() && !state.busy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (state.busy) "再設計中…" else "＋ Visual Projectへ追加")
                    }
                }
            }
        }

        item {
            Text(
                "ドラッグ配置は画面レイアウトとして保存されます。" +
                    "設計要素の追加・変更・削除はDesignCoreを再生成するため、" +
                    "回路・BOM・配線・Firmware・Base44アプリも必要に応じて更新されます。",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            Button(
                onClick = { onOpen(AppScreen.PARTS) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("部品と配線へ進む")
            }
        }
        item {
            OutlinedButton(
                onClick = { onOpen(AppScreen.DESIGN) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("設計へ戻る")
            }
        }
    }
}

@Composable
private fun ProjectGraphCanvas(
    graph: ProjectGraph,
    persistedPositions: Map<String, SavedGraphNodePosition>,
    selectedNodeId: String?,
    onNodeSelect: (String?) -> Unit,
    onNodeMove: (String, SavedGraphNodePosition) -> Unit,
    onNodeMoveFinished: () -> Unit,
) {
    val lanes = graph.lanes.sortedBy { it.order }
    var livePositions by remember(graph) {
        mutableStateOf(resolveGraphPositions(graph, persistedPositions))
    }
    var draggingNodeId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(graph, persistedPositions) {
        livePositions = resolveGraphPositions(graph, persistedPositions)
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.78f)
                .padding(8.dp),
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(graph, livePositions) {
                        detectTapGestures { offset ->
                            if (size.width <= 0 || size.height <= 0) return@detectTapGestures
                            val position = SavedGraphNodePosition(
                                x = offset.x / size.width.toDouble(),
                                y = offset.y / size.height.toDouble(),
                            )
                            onNodeSelect(
                                findGraphNodeAt(
                                    graph = graph,
                                    positions = livePositions,
                                    position = position,
                                )
                            )
                        }
                    }
                    .pointerInput(graph) {
                        detectDragGestures(
                            onDragStart = { offset ->
                                if (size.width <= 0 || size.height <= 0) {
                                    return@detectDragGestures
                                }
                                val position = SavedGraphNodePosition(
                                    x = offset.x / size.width.toDouble(),
                                    y = offset.y / size.height.toDouble(),
                                )
                                draggingNodeId = findGraphNodeAt(
                                    graph = graph,
                                    positions = livePositions,
                                    position = position,
                                )
                                onNodeSelect(draggingNodeId)
                            },
                            onDragEnd = {
                                val nodeId = draggingNodeId
                                val position = nodeId?.let(livePositions::get)
                                if (nodeId != null && position != null) {
                                    onNodeMove(nodeId, position)
                                    onNodeMoveFinished()
                                }
                                draggingNodeId = null
                            },
                            onDragCancel = {
                                draggingNodeId = null
                            },
                        ) { change, dragAmount ->
                            val nodeId = draggingNodeId
                                ?: return@detectDragGestures
                            val current = livePositions[nodeId]
                                ?: return@detectDragGestures
                            if (size.width <= 0 || size.height <= 0) {
                                return@detectDragGestures
                            }
                            change.consume()

                            val requested = SavedGraphNodePosition(
                                x = current.x +
                                    dragAmount.x / size.width.toDouble(),
                                y = current.y +
                                    dragAmount.y / size.height.toDouble(),
                            )
                            val next = clampGraphPosition(
                                graph = graph,
                                nodeId = nodeId,
                                position = requested,
                            )
                            livePositions =
                                livePositions + (nodeId to next)
                        }
                    }
            ) {
                if (lanes.isEmpty()) return@Canvas

                val laneWidth = size.width / lanes.size
                val nodeWidth = laneWidth * 0.78f
                val nodeHeight = (size.height * 0.065f).coerceIn(38f, 54f)

                val titlePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 22f
                    color = android.graphics.Color.DKGRAY
                    textAlign = android.graphics.Paint.Align.CENTER
                }
                val nodePaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 18f
                    color = android.graphics.Color.DKGRAY
                    textAlign = android.graphics.Paint.Align.CENTER
                }

                lanes.forEachIndexed { laneIndex, lane ->
                    val centerX = laneWidth * laneIndex + laneWidth / 2f

                    if (laneIndex > 0) {
                        drawLine(
                            color = Color(0xFFE0E0E0),
                            start = Offset(laneWidth * laneIndex, 0f),
                            end = Offset(laneWidth * laneIndex, size.height),
                            strokeWidth = 1.5f,
                        )
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        lane.title,
                        centerX,
                        28f,
                        titlePaint,
                    )
                }

                graph.edges.forEach { edge ->
                    val startPosition = livePositions[edge.fromNodeId]
                    val endPosition = livePositions[edge.toNodeId]
                    if (startPosition != null && endPosition != null) {
                        drawLine(
                            color = Color(0xFF9E9E9E),
                            start = Offset(
                                (startPosition.x * size.width).toFloat(),
                                (startPosition.y * size.height).toFloat(),
                            ),
                            end = Offset(
                                (endPosition.x * size.width).toFloat(),
                                (endPosition.y * size.height).toFloat(),
                            ),
                            strokeWidth = 2.5f,
                        )
                    }
                }

                graph.nodes.forEach { node ->
                    val normalized = livePositions[node.id] ?: return@forEach
                    val center = Offset(
                        (normalized.x * size.width).toFloat(),
                        (normalized.y * size.height).toFloat(),
                    )
                    val topLeft = Offset(
                        center.x - nodeWidth / 2f,
                        center.y - nodeHeight / 2f,
                    )

                    drawRect(
                        color = projectGraphNodeColor(node.domain),
                        topLeft = topLeft,
                        size = Size(nodeWidth, nodeHeight),
                    )

                    if (node.id == selectedNodeId) {
                        drawRect(
                            color = Color(0xFF212121),
                            topLeft = topLeft,
                            size = Size(nodeWidth, nodeHeight),
                            style = Stroke(width = 4f),
                        )
                    }

                    drawContext.canvas.nativeCanvas.drawText(
                        node.label.take(14),
                        center.x,
                        center.y + 6f,
                        nodePaint,
                    )
                }
            }
        }
    }
}

private fun resolveGraphPositions(
    graph: ProjectGraph,
    persisted: Map<String, SavedGraphNodePosition>,
): Map<String, SavedGraphNodePosition> {
    val lanes = graph.lanes.sortedBy { it.order }
    if (lanes.isEmpty()) return emptyMap()

    val defaults = linkedMapOf<String, SavedGraphNodePosition>()
    lanes.forEachIndexed { laneIndex, lane ->
        val nodes = graph.nodes.filter { it.domain == lane.domain }
        nodes.forEachIndexed { index, node ->
            val x = (laneIndex + 0.5) / lanes.size.toDouble()
            val y = 0.11 + 0.82 * ((index + 1.0) / (nodes.size + 1.0))
            defaults[node.id] = SavedGraphNodePosition(x = x, y = y)
        }
    }

    return defaults.mapValues { (nodeId, fallback) ->
        persisted[nodeId]
            ?.let {
                clampGraphPosition(
                    graph = graph,
                    nodeId = nodeId,
                    position = it,
                )
            }
            ?: fallback
    }
}

private fun clampGraphPosition(
    graph: ProjectGraph,
    nodeId: String,
    position: SavedGraphNodePosition,
): SavedGraphNodePosition {
    val lanes = graph.lanes.sortedBy { it.order }
    val node = graph.nodes.firstOrNull { it.id == nodeId }
        ?: return SavedGraphNodePosition(
            x = position.x.coerceIn(0.02, 0.98),
            y = position.y.coerceIn(0.10, 0.96),
        )
    val laneIndex = lanes.indexOfFirst { it.domain == node.domain }
    if (laneIndex < 0) return position

    val laneStart = laneIndex / lanes.size.toDouble()
    val laneEnd = (laneIndex + 1.0) / lanes.size.toDouble()
    val margin = 0.025

    return SavedGraphNodePosition(
        x = position.x.coerceIn(laneStart + margin, laneEnd - margin),
        y = position.y.coerceIn(0.10, 0.96),
    )
}

private fun findGraphNodeAt(
    graph: ProjectGraph,
    positions: Map<String, SavedGraphNodePosition>,
    position: SavedGraphNodePosition,
): String? {
    val laneCount = graph.lanes.size.coerceAtLeast(1)
    val halfWidth = (0.78 / laneCount) / 2.0
    val halfHeight = 0.042

    return graph.nodes
        .asReversed()
        .firstOrNull { node ->
            val center = positions[node.id] ?: return@firstOrNull false
            kotlin.math.abs(center.x - position.x) <= halfWidth &&
                kotlin.math.abs(center.y - position.y) <= halfHeight
        }
        ?.id
}

private fun visualCapabilityLabel(capabilityId: String): String = when (capabilityId) {
    "measure_temperature" -> "温度測定"
    "measure_humidity" -> "湿度測定"
    "actuate_fan" -> "ファン制御"
    else -> capabilityId
}

private fun projectGraphNodeColor(domain: ProjectGraphDomain): Color = when (domain) {
    ProjectGraphDomain.HARDWARE -> Color(0xFFE3F2FD)
    ProjectGraphDomain.BEHAVIOR -> Color(0xFFFFF3E0)
    ProjectGraphDomain.APPLICATION -> Color(0xFFE8F5E9)
    ProjectGraphDomain.RUNTIME -> Color(0xFFF3E5F5)
}


@Composable
private fun RevisionScreen(
    state: BuilderAppState,
    onAdditionalRequestChange: (String) -> Unit,
    onApplyAdditionalRequest: () -> Unit,
    onOpen: (AppScreen) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "AIと会話しながら追加機能や条件変更を整理します。仕様が揃うと自動で再設計します。",
                style = MaterialTheme.typography.bodyMedium,
            )
            if (state.revisionAssistantLabel.isNotBlank()) {
                Text(
                    state.revisionAssistantLabel,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }

        items(
            items = state.revisionMessages,
            key = { it.id },
        ) { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        if (message.speaker == RevisionSpeaker.USER) "あなた" else "設計アシスタント",
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(message.text)
                }
            }
        }

        item {
            OutlinedTextField(
                value = state.additionalRequestText,
                onValueChange = onAdditionalRequestChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 2,
                label = {
                    Text(
                        if (state.revisionPendingSlotId == null) {
                            "追加要望・変更内容・回答"
                        } else {
                            "質問への回答"
                        }
                    )
                },
                enabled = !state.busy,
                supportingText = {
                    Text("GPIOや配線はAIに決めさせず、決定論的な安全検証で確定します。")
                },
            )
        }
        item {
            Button(
                onClick = onApplyAdditionalRequest,
                enabled = state.additionalRequestText.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "確認中…" else "送信")
            }
        }

        val candidate =
            state.revisionCandidateGoalText.ifBlank { state.goalText }
        if (candidate.isNotBlank()) {
            item {
                InfoCard(
                    "対話中の仕様",
                    candidate,
                )
            }
        }

        item {
            OutlinedButton(
                onClick = { onOpen(AppScreen.DESIGN) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("設計へ戻る（会話は保持）")
            }
        }
    }
}

@Composable
private fun PartsScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
) {
    val bundle = state.bundle ?: return
    val catalog = GoldenEngineeringCatalog
    val rows = buildList {
        val board = catalog.board(bundle.designIr.board.boardId)
        add("1 × " + (board?.displayName ?: bundle.designIr.board.boardId) + " — マイコン")

        bundle.designIr.components.forEach { instance ->
            val spec = catalog.component(instance.componentId)
            add("1 × " + (spec?.displayName ?: instance.componentId) + " — " + instance.role)
        }

        bundle.designIr.power.sources
            .filter { it.componentId != null }
            .forEach { source ->
                val supply = catalog.powerSupplies()
                    .firstOrNull { it.componentId == source.componentId }
                add("1 × " + (supply?.displayName ?: source.componentId.orEmpty()) + " — 外部電源")
            }
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        items(rows) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = row,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
        item {
            Button(
                onClick = { onOpen(AppScreen.WIRING) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("配線を見る")
            }
        }
        item {
            OutlinedButton(
                onClick = { onOpen(AppScreen.DESIGN) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("戻る")
            }
        }
    }
}

@Composable
private fun WiringScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
) {
    val bundle = state.bundle ?: return

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("この配線図は検証済みCircuitGraphから生成されています。")
        WiringDiagram(
            spec = bundle.diagramSpec,
            highlighted = emptySet(),
        )
        Button(
            onClick = { onOpen(AppScreen.BUILD) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("1本ずつ組み立てる")
        }
        OutlinedButton(
            onClick = { onOpen(AppScreen.PARTS) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("戻る")
        }
    }
}

@Composable
private fun BuildScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
    onBuildProgress: (Set<String>, Int) -> Unit,
) {
    val bundle = state.bundle ?: return
    val plan = bundle.diagramSpec.buildPlan ?: return
    val targetOrder = state.buildTargetStepOrder
    val machine = remember(
        plan,
        targetOrder,
        state.projectId,
        state.currentBuildStepIndex,
        state.completedConnectionIds,
    ) {
        GuidedBuildStateMachine(
            plan = plan,
            snapshot = GuidedBuildSnapshot(
                completedConnectionIds = state.completedConnectionIds,
                currentStepIndex = state.currentBuildStepIndex,
            ),
        ).also { stateMachine ->
            if (targetOrder != null && targetOrder in 1..plan.steps.size) {
                stateMachine.goToStep(targetOrder)
            }
        }
    }
    var buildState by remember(
        plan,
        targetOrder,
        state.projectId,
        state.currentBuildStepIndex,
        state.completedConnectionIds,
    ) {
        mutableStateOf(machine.state())
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LinearProgressIndicator(
            progress = buildState.progress.toFloat(),
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            "${buildState.completedCount} / ${buildState.totalCount}",
            style = MaterialTheme.typography.labelLarge,
        )

        buildState.currentStep?.let { step ->
            Text(step.title, style = MaterialTheme.typography.titleLarge)
            Text(step.instruction)
            Text(step.safetyNote, style = MaterialTheme.typography.bodySmall)

            WiringDiagram(
                spec = bundle.diagramSpec,
                highlighted = setOf(step.connectionId),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        buildState = machine.back()
                        val snapshot = machine.snapshot()
                        onBuildProgress(
                            snapshot.completedConnectionIds,
                            snapshot.currentStepIndex,
                        )
                    },
                ) {
                    Text("前へ")
                }
                Button(
                    onClick = {
                        buildState = machine.markCurrentCompleted()
                        val snapshot = machine.snapshot()
                        onBuildProgress(
                            snapshot.completedConnectionIds,
                            snapshot.currentStepIndex,
                        )
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text("接続済み")
                }
            }
        }

        if (buildState.currentStep == null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text("組立完了", style = MaterialTheme.typography.titleLarge)
                    Text("次に通電前確認と装置設定へ進みます。")
                }
            }
            Button(
                onClick = { onOpen(AppScreen.CONNECT) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("装置へ設定")
            }
        }
    }
}

@Composable
private fun ConnectScreen(
    state: BuilderAppState,
    onConnect: () -> Unit,
    onDeploy: () -> Unit,
    onBridgePairingCodeChange: (String) -> Unit,
    onPairBase44: () -> Unit,
    onOpen: (AppScreen) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            "接続",
            if (state.connection == null) "未接続" else "BLE接続済み",
        )

        val needsBase44 =
            state.bundle?.softwarePlan?.base44DesignRequired == true

        if (needsBase44) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "CircuitFlow / Base44",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        if (state.base44BridgeStatus.isBlank()) {
                            "CircuitFlowで「接続コードを発行」して、ここへ入力します。"
                        } else {
                            state.base44BridgeStatus
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    if (!state.base44BridgeOnline) {
                        OutlinedTextField(
                            value = state.bridgePairingCode,
                            onValueChange = onBridgePairingCodeChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("接続コード") },
                            singleLine = true,
                        )
                        Button(
                            onClick = onPairBase44,
                            enabled =
                                state.bridgePairingCode.isNotBlank() &&
                                    !state.busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("CircuitFlowと接続")
                        }
                    } else {
                        InfoCard(
                            "Bridge",
                            "接続済み / 画面表示中は3秒同期、バックグラウンドでも10秒ごとに接続維持",
                        )
                    }
                }
            }
        }

        if (state.deployMessage.isNotBlank()) {
            Text(state.deployMessage)
        }

        if (state.deployProgress > 0) {
            LinearProgressIndicator(
                progress = state.deployProgress / 100f,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        if (state.connection == null) {
            Button(
                onClick = onConnect,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "接続中…" else "装置を探して接続")
            }
        } else {
            Button(
                onClick = onDeploy,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "設定中…" else "装置へ設定")
            }
            OutlinedButton(
                onClick = { onOpen(AppScreen.BENCH) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("実機ベンチ試験")
            }
        }

        OutlinedButton(
            onClick = { onOpen(AppScreen.BUILD) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("組立へ戻る")
        }
    }
}

@Composable
private fun ControlScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
    onOpenBuildStep: (Int) -> Unit,
) {
    val bundle = state.bundle ?: return
    val connection = state.connection

    if (connection == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("装置への接続がありません。")
            Button(
                onClick = { onOpen(AppScreen.CONNECT) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("接続画面へ")
            }
        }
        return
    }

    RuntimeControlDashboard(
        transport = connection.transport,
        uiSpec = bundle.uiSpec,
        tests = bundle.testPlan.tests,
        diagnostics = bundle.designIr.diagnostics,
        diagramSpec = bundle.diagramSpec,
        onOpenBuildStep = onOpenBuildStep,
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun InfoCard(title: String, value: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelMedium)
            Text(value, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun WiringDiagram(
    spec: DiagramSpec,
    highlighted: Set<String>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(spec.canvasWidth.toFloat() / spec.canvasHeight.toFloat())
                .padding(8.dp),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val scaleX = size.width / spec.canvasWidth
                val scaleY = size.height / spec.canvasHeight
                val scale = minOf(scaleX, scaleY)
                val textPaint = android.graphics.Paint().apply {
                    isAntiAlias = true
                    textSize = 22f * scale.coerceAtLeast(0.7f)
                    color = android.graphics.Color.DKGRAY
                }

                spec.placements.forEach { placement ->
                    val left = placement.origin.x.toFloat() * scale
                    val top = placement.origin.y.toFloat() * scale
                    val width = placement.size.width.toFloat() * scale
                    val height = placement.size.height.toFloat() * scale

                    drawRect(
                        color = Color(0xFFF4F4F4),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                    )
                    drawContext.canvas.nativeCanvas.drawText(
                        placement.label,
                        left + 8f,
                        top + 24f * scale.coerceAtLeast(0.7f),
                        textPaint,
                    )

                    placement.pins.forEach { pin ->
                        drawCircle(
                            color = Color.DarkGray,
                            radius = 5f * scale.coerceAtLeast(0.7f),
                            center = Offset(
                                pin.point.x.toFloat() * scale,
                                pin.point.y.toFloat() * scale,
                            ),
                        )
                    }
                }

                val hasHighlight = highlighted.isNotEmpty()
                spec.wires.forEach { wire ->
                    val isHighlighted = wire.connectionId in highlighted
                    val alpha = if (hasHighlight && !isHighlighted) 0.12f else 1f
                    val stroke = if (isHighlighted) 9f else 5f

                    wire.points.zipWithNext().forEach { pair ->
                        val a = pair.first
                        val b = pair.second
                        drawLine(
                            color = wireColor(wire.netType).copy(alpha = alpha),
                            start = Offset(
                                a.x.toFloat() * scale,
                                a.y.toFloat() * scale,
                            ),
                            end = Offset(
                                b.x.toFloat() * scale,
                                b.y.toFloat() * scale,
                            ),
                            strokeWidth = stroke * scale.coerceAtLeast(0.65f),
                        )
                    }
                }
            }
        }
    }
}

private fun wireColor(netType: NetType): Color = when (netType) {
    NetType.POWER -> Color(0xFFD32F2F)
    NetType.GROUND -> Color(0xFF202020)
    NetType.I2C_SDA -> Color(0xFF2E7D32)
    NetType.I2C_SCL -> Color(0xFFEF6C00)
    NetType.CONTROL -> Color(0xFF1565C0)
    NetType.LOAD -> Color(0xFF6A1B9A)
    else -> Color(0xFF546E7A)
}


@Composable
private fun AdvancedEditorScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
) {
    val bundle = state.bundle ?: return

    AdvancedProjectEditor(
        bundle = bundle,
        onBack = { onOpen(AppScreen.DESIGN) },
        modifier = Modifier.fillMaxSize(),
    )
}


@Composable
private fun BenchScreen(
    state: BuilderAppState,
    onOpen: (AppScreen) -> Unit,
) {
    val bundle = state.bundle ?: return
    val connection = state.connection

    if (connection == null) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("装置への接続がありません。")
            Button(
                onClick = { onOpen(AppScreen.CONNECT) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("接続画面へ")
            }
        }
        return
    }

    BenchGateScreen(
        bundle = bundle,
        transport = connection.transport,
        onBack = { onOpen(AppScreen.CONNECT) },
        modifier = Modifier.fillMaxSize(),
    )
}
