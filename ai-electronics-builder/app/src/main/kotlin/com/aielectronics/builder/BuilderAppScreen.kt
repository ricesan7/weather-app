package com.aielectronics.builder

import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.aielectronics.application.DesignExplanationBuilder
import com.aielectronics.assembly.GuidedBuildSnapshot
import com.aielectronics.assembly.GuidedBuildStateMachine
import com.aielectronics.bench.BenchGateScreen
import com.aielectronics.control.RuntimeControlDashboard
import com.aielectronics.editor.AdvancedProjectEditor
import com.aielectronics.core.model.DiagramSpec
import com.aielectronics.core.model.NetType
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
    onBuildProgress: (Set<String>, Int) -> Unit,
    onResumeProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onNewProject: () -> Unit,
    onOpenBuildStep: (Int) -> Unit,
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
                onResumeProject = onResumeProject,
                onDeleteProject = onDeleteProject,
                onNewProject = onNewProject,
            )
            AppScreen.DESIGN -> DesignScreen(state, onOpen)
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
    onResumeProject: (String) -> Unit,
    onDeleteProject: (String) -> Unit,
    onNewProject: () -> Unit,
) {
    var answer by remember(state.pendingQuestions.firstOrNull()?.slotId) {
        mutableStateOf("")
    }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "例：温度が30℃以上になったらファンを回したい。履歴もスマホで見たい。",
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
        state.lastSavedAtEpochMs?.let {
            item {
                InfoCard("保存", "このプロジェクトは端末に自動保存されています")
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
private fun RevisionScreen(
    state: BuilderAppState,
    onAdditionalRequestChange: (String) -> Unit,
    onApplyAdditionalRequest: () -> Unit,
    onOpen: (AppScreen) -> Unit,
) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                "現在の設計に追加したいことや、変更したい条件を自然な言葉で入力してください。",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        item {
            InfoCard(
                "現在の要件",
                state.goalText,
            )
        }
        item {
            OutlinedTextField(
                value = state.additionalRequestText,
                onValueChange = onAdditionalRequestChange,
                modifier = Modifier.fillMaxWidth(),
                minLines = 4,
                label = { Text("追加要望・変更内容") },
                enabled = !state.busy,
                supportingText = {
                    Text("反映すると部品・配線・制御・操作画面を安全検証から再生成します。")
                },
            )
        }
        item {
            Button(
                onClick = onApplyAdditionalRequest,
                enabled = state.additionalRequestText.isNotBlank() && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.busy) "再設計中…" else "追加要望を反映して再設計")
            }
        }
        item {
            OutlinedButton(
                onClick = { onOpen(AppScreen.DESIGN) },
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("変更せず戻る")
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
    onOpen: (AppScreen) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        InfoCard(
            "接続",
            if (state.connection == null) "未接続" else "BLE接続済み",
        )

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
