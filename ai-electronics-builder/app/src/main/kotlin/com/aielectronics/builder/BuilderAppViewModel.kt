package com.aielectronics.builder

import android.content.Context
import android.provider.Settings
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.aielectronics.application.ApplicationProjectEngine
import com.aielectronics.application.BeginnerErrorPresenter
import com.aielectronics.application.BeginnerIntentInterpreter
import com.aielectronics.application.FrictionSnapshot
import com.aielectronics.application.FrictionTelemetryRecorder
import com.aielectronics.application.InMemoryProjectRepository
import com.aielectronics.application.ProjectRepository
import com.aielectronics.application.ProjectTitle
import com.aielectronics.application.SavedGraphNodePosition
import com.aielectronics.application.SavedProject
import com.aielectronics.application.VisualAppLayoutResolver
import com.aielectronics.ble.android.AndroidBleRuntimeConnector
import com.aielectronics.compiler.CompileResult
import com.aielectronics.compiler.RequirementResolution
import com.aielectronics.core.model.AppBridgeDirection
import com.aielectronics.core.model.AppHardwareIntegrationContract
import com.aielectronics.core.model.ComponentResearchRecord
import com.aielectronics.core.model.ComponentResearchRequest
import com.aielectronics.core.model.ComponentVerificationStatus
import com.aielectronics.core.model.ProjectGraphNodeKind
import com.aielectronics.core.model.ReleaseBundle
import com.aielectronics.core.model.ResolvedRequirements
import com.aielectronics.control.RuntimeControlClient
import com.aielectronics.runtime.CanonicalManifestEncoder
import com.aielectronics.runtime.RuntimeClient
import com.aielectronics.parts.ComponentResearchStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class BuilderAppViewModel(
    private val interpreter: BeginnerIntentInterpreter = BeginnerIntentInterpreter(),
    private val engine: ApplicationProjectEngine = ApplicationProjectEngine(),
    private val frictionTelemetry: FrictionTelemetryRecorder = FrictionTelemetryRecorder(),
    private val projectRepository: ProjectRepository = InMemoryProjectRepository(),
    private val revisionAssistant: RevisionLanguageAssistant = LocalRevisionLanguageAssistant(),
    private val base44BridgeClient: Base44HardwareBridgeClient? =
        BuildConfig.BASE44_BRIDGE_URL
            .takeIf { it.isNotBlank() }
            ?.let(::Base44HardwareBridgeClient),
    private val bridgeCredentialStore: Base44BridgeCredentialStore? = null,
    private val componentResearchClient: ComponentResearchClient? = null,
    private val componentResearchStore: ComponentResearchStore? = null,
) : ViewModel() {

    private val localRevisionAssistant = LocalRevisionLanguageAssistant()
    private var bridgeJob: Job? = null

    private val _state = MutableStateFlow(BuilderAppState())
    val state: StateFlow<BuilderAppState> = _state.asStateFlow()

    private val _friction = MutableStateFlow(frictionTelemetry.snapshot())
    val friction: StateFlow<FrictionSnapshot> = _friction.asStateFlow()

    init {
        _state.update {
            it.copy(revisionAssistantLabel = revisionAssistant.statusLabel)
        }
        if (projectRepository is InMemoryProjectRepository) {
            _state.update {
                it.copy(savedProjects = projectRepository.list())
            }
        } else {
            refreshSavedProjects()
        }
    }

    fun setGoal(text: String) {
        _state.update { it.copy(goalText = text) }
    }

    fun setAdditionalRequest(text: String) {
        _state.update { it.copy(additionalRequestText = text) }
    }

    fun selectGraphNode(nodeId: String?) {
        val graph = _state.value.bundle?.projectGraph ?: return
        val selected = nodeId?.takeIf { candidate ->
            graph.nodes.any { it.id == candidate }
        }
        _state.update {
            it.copy(
                selectedGraphNodeId = selected,
                error = null,
            )
        }
    }

    fun moveGraphNode(
        nodeId: String,
        position: SavedGraphNodePosition,
    ) {
        val graph = _state.value.bundle?.projectGraph ?: return
        if (graph.nodes.none { it.id == nodeId }) return

        val normalized = SavedGraphNodePosition(
            x = position.x.coerceIn(0.02, 0.98),
            y = position.y.coerceIn(0.10, 0.96),
        )
        _state.update {
            val previousPositions = it.graphNodePositions
            val nextPositions =
                previousPositions + (nodeId to normalized)
            if (nextPositions == previousPositions) {
                it.copy(selectedGraphNodeId = nodeId)
            } else {
                it.copy(
                    selectedGraphNodeId = nodeId,
                    graphNodePositions = nextPositions,
                    graphLayoutUndoStack =
                        (it.graphLayoutUndoStack + previousPositions)
                            .takeLast(MAX_GRAPH_LAYOUT_HISTORY),
                    graphLayoutRedoStack = emptyList(),
                )
            }
        }
    }

    fun finishGraphNodeMove() {
        persistCurrent()
    }

    fun undoGraphLayout() {
        _state.update { state ->
            val previous = state.graphLayoutUndoStack.lastOrNull()
                ?: return@update state
            state.copy(
                graphNodePositions = previous,
                graphLayoutUndoStack = state.graphLayoutUndoStack.dropLast(1),
                graphLayoutRedoStack =
                    (state.graphLayoutRedoStack + state.graphNodePositions)
                        .takeLast(MAX_GRAPH_LAYOUT_HISTORY),
            )
        }
        persistCurrent()
    }

    fun redoGraphLayout() {
        _state.update { state ->
            val next = state.graphLayoutRedoStack.lastOrNull()
                ?: return@update state
            state.copy(
                graphNodePositions = next,
                graphLayoutUndoStack =
                    (state.graphLayoutUndoStack + state.graphNodePositions)
                        .takeLast(MAX_GRAPH_LAYOUT_HISTORY),
                graphLayoutRedoStack = state.graphLayoutRedoStack.dropLast(1),
            )
        }
        persistCurrent()
    }

    fun addGraphElement(description: String) {
        val clean = description.trim()
        if (clean.isBlank()) {
            _state.update { it.copy(error = "追加したいものを入力してください。") }
            return
        }
        submitVisualRevision(
            "Visual Projectから次の要素を追加してください。\n" +
                "追加内容: " + clean + "\n" +
                "既存の回路・電源・ファームウェア・Base44アプリ・Hardware Bridgeとの整合性を保ち、" +
                "必要な変更をすべて同じプロジェクトへ反映してください。"
        )
    }

    fun changeGraphNode(
        nodeId: String,
        instruction: String,
    ) {
        val clean = instruction.trim()
        if (clean.isBlank()) {
            _state.update { it.copy(error = "変更内容を入力してください。") }
            return
        }
        val node = _state.value.bundle
            ?.projectGraph
            ?.nodes
            ?.firstOrNull { it.id == nodeId }
            ?: run {
                _state.update { it.copy(error = "選択した要素が見つかりません。") }
                return
            }

        submitVisualRevision(
            "Visual Project上の要素を変更してください。\n" +
                "対象: " + node.label + " (" + node.kind.name + ")\n" +
                "変更内容: " + clean + "\n" +
                "変更後は回路・BOM・配線・動作・ファームウェア・Base44アプリ・Hardware Bridgeを" +
                "必要に応じて再生成し、安全検証を通してください。"
        )
    }

    fun deleteGraphNode(nodeId: String) {
        val node = _state.value.bundle
            ?.projectGraph
            ?.nodes
            ?.firstOrNull { it.id == nodeId }
            ?: run {
                _state.update { it.copy(error = "選択した要素が見つかりません。") }
                return
            }

        if (
            node.kind == ProjectGraphNodeKind.BOARD ||
            node.kind == ProjectGraphNodeKind.RUNTIME
        ) {
            _state.update {
                it.copy(
                    error =
                        "マイコン本体とHardware Runtimeは直接削除できません。" +
                            "置き換えたい場合は「変更」を使用してください。"
                )
            }
            return
        }

        submitVisualRevision(
            "Visual Project上の次の要素を削除してください。\n" +
                "対象: " + node.label + " (" + node.kind.name + ")\n" +
                "この要素に依存する配線・電源・動作ルール・ファームウェア・Base44 UIも確認し、" +
                "不要になった依存要素だけを安全に整理してください。"
        )
    }

    private fun submitVisualRevision(request: String) {
        val current = _state.value
        if (current.projectId == null || current.bundle == null) {
            _state.update { it.copy(error = "先に設計を作成してください。") }
            return
        }
        if (current.busy) return

        _state.update {
            it.copy(
                screen = AppScreen.REVISION,
                additionalRequestText = request,
                revisionReturnScreen = AppScreen.GRAPH,
                revisionCandidateGoalText =
                    it.revisionCandidateGoalText.ifBlank { it.goalText },
                error = null,
            )
        }
        applyAdditionalRequest()
    }

    fun setBridgePairingCode(text: String) {
        _state.update {
            it.copy(
                bridgePairingCode = text.uppercase().replace(" ", ""),
                error = null,
            )
        }
    }

    fun pairBase44(context: Context) {
        val bridge = base44BridgeClient ?: run {
            _state.update { it.copy(error = "Base44 Bridge URLが設定されていません。") }
            return
        }
        val localProjectId = _state.value.projectId ?: run {
            _state.update { it.copy(error = "先にプロジェクトを作成してください。") }
            return
        }
        val code = _state.value.bridgePairingCode.trim()
        if (code.isBlank()) {
            _state.update { it.copy(error = "CircuitFlowで発行した接続コードを入力してください。") }
            return
        }

        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: UUID.randomUUID().toString()

        _state.update {
            it.copy(
                base44BridgeStatus = "CircuitFlowへ接続しています…",
                base44BridgeOnline = false,
                error = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    bridge.pair(code, deviceId)
                }
            }.onSuccess { credentials ->
                bridgeCredentialStore?.save(localProjectId, credentials)
                Base44BridgeKeepAliveService.start(
                    context.applicationContext,
                    localProjectId,
                )
                _state.update {
                    it.copy(
                        bridgePairingCode = "",
                        base44BridgeStatus = "CircuitFlowとペアリングしました。",
                        base44BridgeOnline = true,
                    )
                }
                startBridgeSync(credentials)
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        base44BridgeStatus = "CircuitFlowとのペアリングに失敗しました。",
                        base44BridgeOnline = false,
                        error = throwable.message ?: "Base44 Bridgeへの接続に失敗しました。",
                    )
                }
            }
        }
    }

    fun receiveBase44Design(context: Context) {
        val bridge = base44BridgeClient ?: run {
            _state.update {
                it.copy(error = "Base44 Bridge URLが設定されていません。")
            }
            return
        }
        val code = _state.value.bridgePairingCode.trim()
        if (code.isBlank()) {
            _state.update {
                it.copy(
                    error =
                        "Base44で仕様確定時に表示された接続コードを入力してください。"
                )
            }
            return
        }
        if (_state.value.busy) return

        val deviceId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID,
        ) ?: UUID.randomUUID().toString()

        _state.update {
            it.copy(
                busy = true,
                error = null,
                base44BridgeOnline = false,
                base44BridgeStatus = "Base44から確定仕様を受信しています…",
                base44HandoffStatus = "receiving",
                base44HandoffMessage = "",
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val credentials = bridge.pair(code, deviceId)
                    val sync = bridge.sync(
                        credentials = credentials,
                        telemetry = emptyMap(),
                        settings = emptyMap(),
                        contract = null,
                        hardwareConnected = false,
                        requestDesignRecovery = true,
                    )
                    val handoff = sync.designHandoff
                        ?: error("Base44に受け取り可能な確定仕様がありません。")
                    credentials to handoff
                }
            }.onSuccess { (credentials, handoff) ->
                val localProjectId = UUID.randomUUID().toString()
                val now = System.currentTimeMillis()
                bridgeCredentialStore?.save(localProjectId, credentials)
                Base44BridgeKeepAliveService.start(
                    context.applicationContext,
                    localProjectId,
                )

                val savedProjects = _state.value.savedProjects
                _state.value = BuilderAppState(
                    screen = AppScreen.HOME,
                    goalText = handoff.goalText,
                    busy = false,
                    projectId = localProjectId,
                    projectTitle = handoff.title,
                    projectCreatedAtEpochMs = now,
                    savedProjects = savedProjects,
                    bridgePairingCode = "",
                    base44BridgeStatus =
                        "Base44の確定仕様を受信しました。自動設計を開始します。",
                    base44BridgeOnline = true,
                    base44HandoffRevision = handoff.revision,
                    base44HandoffStatus = "processing",
                    base44HandoffMessage =
                        "Project Compilerで回路・部品・配線・Firmwareを再検証しています。",
                    revisionAssistantLabel =
                        revisionAssistant.statusLabel,
                )
                persistCurrent()
                startBridgeSync(credentials)
                resolveAndCompile()
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        busy = false,
                        base44BridgeOnline = false,
                        base44HandoffStatus = "failed",
                        base44BridgeStatus = "Base44仕様の受信に失敗しました。",
                        error = throwable.message
                            ?: "Base44から確定仕様を受信できませんでした。",
                    )
                }
            }
        }
    }

    fun applyAdditionalRequest() {
        val current = _state.value
        val requestText = current.additionalRequestText.trim()
        if (requestText.isBlank()) {
            _state.update { it.copy(error = "追加したい要望や回答を入力してください。") }
            return
        }
        if (current.projectId == null || current.bundle == null) {
            _state.update { it.copy(error = "先に設計を作成してください。") }
            return
        }
        if (current.busy) return

        val userMessage = RevisionChatMessage(
            id = UUID.randomUUID().toString(),
            speaker = RevisionSpeaker.USER,
            text = requestText,
        )
        val history = current.revisionMessages + userMessage
        val candidateGoal =
            current.revisionCandidateGoalText.ifBlank { current.goalText }
        val revisedClarifications =
            current.revisionPendingSlotId?.let { slotId ->
                current.revisionClarificationValues + (slotId to requestText)
            } ?: current.revisionClarificationValues

        _state.update {
            it.copy(
                additionalRequestText = "",
                revisionMessages = history,
                revisionClarificationValues = revisedClarifications,
                revisionPendingSlotId = null,
                busy = true,
                error = null,
            )
        }

        viewModelScope.launch {
            try {
                val languageRequest = RevisionLanguageRequest(
                    currentGoal = candidateGoal,
                    userMessage = requestText,
                    history = history,
                )
                val primary = withContext(Dispatchers.IO) {
                    revisionAssistant.refine(languageRequest)
                }
                val languageResult =
                    if (primary.isSuccess) {
                        primary.getOrThrow()
                    } else {
                        val fallback = withContext(Dispatchers.Default) {
                            localRevisionAssistant.refine(languageRequest).getOrThrow()
                        }
                        fallback.copy(
                            assistantMessage =
                                "AIゲートウェイへ接続できなかったため、ローカル確認に切り替えました。\n" +
                                    fallback.assistantMessage,
                        )
                    }

                if (languageResult.needsClarification) {
                    val assistantText = listOf(
                        languageResult.assistantMessage,
                        languageResult.clarificationQuestion,
                    )
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString("\n")

                    _state.update {
                        it.copy(
                            busy = false,
                            revisionCandidateGoalText = languageResult.updatedGoal,
                            revisionClarificationValues = revisedClarifications,
                            revisionMessages =
                                it.revisionMessages + assistantMessage(assistantText),
                        )
                    }
                    return@launch
                }

                when (
                    val result = withContext(Dispatchers.Default) {
                        resolveRevisionCandidate(
                            goal = languageResult.updatedGoal,
                            clarifications = revisedClarifications,
                        )
                    }
                ) {
                    is ResolutionResult.Questions -> {
                        val question = result.questions.firstOrNull()
                        if (question == null) {
                            _state.update {
                                it.copy(
                                    busy = false,
                                    revisionCandidateGoalText = languageResult.updatedGoal,
                                    revisionMessages =
                                        it.revisionMessages + assistantMessage(
                                            languageResult.assistantMessage
                                        ),
                                )
                            }
                            return@launch
                        }

                        recordFriction {
                            recordQuestionPresented(question.slotId)
                        }
                        val assistantText = listOf(
                            languageResult.assistantMessage,
                            "確認です。" + question.userQuestion,
                        )
                            .filter { it.isNotBlank() }
                            .distinct()
                            .joinToString("\n")

                        _state.update {
                            it.copy(
                                busy = false,
                                revisionCandidateGoalText = languageResult.updatedGoal,
                                revisionClarificationValues = revisedClarifications,
                                revisionPendingSlotId = question.slotId,
                                revisionMessages =
                                    it.revisionMessages + assistantMessage(assistantText),
                            )
                        }
                    }

                    is ResolutionResult.Research -> {
                        runComponentResearch(
                            requests = result.requests,
                            retry = ::resolveAndCompile,
                        )
                    }

                    is ResolutionResult.Success -> {
                        commitRevision(
                            updatedGoal = languageResult.updatedGoal,
                            clarifications = revisedClarifications,
                            requirements = result.requirements,
                            bundle = result.bundle,
                        )
                    }

                    is ResolutionResult.Error -> {
                        val assistantText = listOf(
                            languageResult.assistantMessage,
                            result.message,
                            "条件を修正して続けて入力してください。現在の安全な設計は変更していません。",
                        )
                            .filter { it.isNotBlank() }
                            .joinToString("\n")

                        _state.update {
                            it.copy(
                                busy = false,
                                revisionCandidateGoalText = languageResult.updatedGoal,
                                revisionClarificationValues = revisedClarifications,
                                revisionMessages =
                                    it.revisionMessages + assistantMessage(assistantText),
                            )
                        }
                    }
                }
            } catch (throwable: Throwable) {
                _state.update {
                    it.copy(
                        busy = false,
                        error = throwable.message ?: "追加要望の対話処理に失敗しました。",
                    )
                }
            }
        }
    }

    fun newProject() {
        bridgeJob?.cancel()
        bridgeJob = null
        val saved = _state.value.savedProjects
        _state.value = BuilderAppState(savedProjects = saved)
    }

    fun startDesign() {
        val goal = _state.value.goalText.trim()
        if (goal.isBlank()) {
            _state.update { it.copy(error = "作りたいものを入力してください。") }
            return
        }
        recordFriction { recordGoalSubmitted() }
        resolveAndCompile()
    }

    fun answerQuestion(slotId: String, answer: String) {
        if (answer.isBlank()) return
        _state.update {
            it.copy(
                clarificationValues = it.clarificationValues + (slotId to answer.trim()),
                error = null,
            )
        }
        recordFriction { recordQuestionAnswered(slotId) }
        resolveAndCompile()
    }

    fun open(screen: AppScreen) {
        val from = _state.value.screen
        recordFriction {
            recordScreenTransition(
                from = from.name,
                to = screen.name,
                userInitiated = true,
            )
        }
        _state.update {
            if (screen == AppScreen.REVISION && it.revisionMessages.isEmpty()) {
                it.copy(
                    screen = screen,
                    error = null,
                    additionalRequestText = "",
                    revisionCandidateGoalText = it.goalText,
                    revisionClarificationValues = emptyMap(),
                    revisionPendingSlotId = null,
                    revisionStatusMessage = "",
                    revisionReturnScreen = null,
                    revisionMessages = listOf(
                        assistantMessage(
                            "追加したい機能や変更したい条件を教えてください。" +
                                "必要な確認をしながら仕様を固め、揃った時点で自動的に再設計します。"
                        )
                    ),
                )
            } else {
                it.copy(screen = screen, error = null)
            }
        }
        persistCurrent()
    }

    fun confirmBuildStep(connectionId: String? = null) {
        if (connectionId != null) {
            recordFriction { recordGuidedBuildConfirmation(connectionId) }
        }
    }

    fun updateBuildProgress(
        completedConnectionIds: Set<String>,
        currentStepIndex: Int,
    ) {
        val previousCompleted = _state.value.completedConnectionIds
        val newlyCompleted = completedConnectionIds - previousCompleted

        _state.update {
            it.copy(
                completedConnectionIds = completedConnectionIds,
                currentBuildStepIndex = currentStepIndex,
                buildTargetStepOrder = null,
            )
        }

        newlyCompleted.forEach { connectionId ->
            recordFriction { recordGuidedBuildConfirmation(connectionId) }
        }
        persistCurrent()
    }

    fun openBuildStep(order: Int) {
        val from = _state.value.screen
        recordFriction {
            recordScreenTransition(
                from = from.name,
                to = AppScreen.BUILD.name,
                userInitiated = true,
            )
        }
        _state.update {
            it.copy(
                screen = AppScreen.BUILD,
                buildTargetStepOrder = order,
                error = null,
            )
        }
        persistCurrent()
    }

    fun resumeProject(projectId: String) {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val saved = projectRepository.load(projectId)
                        ?: error("保存したプロジェクトが見つかりません。")
                    val intent = interpreter.interpret(
                        text = saved.goalText,
                        clarifications = saved.clarificationValues,
                    )

                    when (val resolution = engine.resolve(intent)) {
                        is RequirementResolution.NeedUserInput ->
                            ResumeResult.NeedsInput(saved, resolution)

                        is RequirementResolution.Ready ->
                            when (val compile = engine.compile(resolution.requirements)) {
                                is CompileResult.Success ->
                                    ResumeResult.Ready(
                                        saved = saved,
                                        requirements = resolution.requirements,
                                        bundle = compile.bundle,
                                    )

                                is CompileResult.NeedUserInput ->
                                    ResumeResult.NeedsCompileInput(
                                        saved = saved,
                                        questions = compile.questions,
                                    )

                                is CompileResult.NeedsComponentResearch ->
                                    ResumeResult.Research(
                                        saved = saved,
                                        requests = compile.requests,
                                    )

                                is CompileResult.Blocked ->
                                    ResumeResult.Error(
                                        BeginnerErrorPresenter.validationBlocked(
                                            compile.report
                                        )
                                    )

                                is CompileResult.Failed ->
                                    ResumeResult.Error(
                                        BeginnerErrorPresenter.compileFailure(
                                            compile.error
                                        )
                                    )
                            }
                    }
                }
            }.onSuccess { result ->
                when (result) {
                    is ResumeResult.Ready -> {
                        restoreReadyProject(result)
                        maybeStartBridgeSync()
                    }
                    is ResumeResult.Research -> {
                        _state.update {
                            it.copy(
                                projectId = result.saved.id,
                                projectTitle = result.saved.title,
                                projectCreatedAtEpochMs =
                                    result.saved.createdAtEpochMs,
                                goalText = result.saved.goalText,
                                clarificationValues =
                                    result.saved.clarificationValues,
                                screen = AppScreen.HOME,
                            )
                        }
                        runComponentResearch(
                            requests = result.requests,
                            retry = {
                                resumeProject(result.saved.id)
                            },
                        )
                    }

                    is ResumeResult.NeedsInput -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                projectId = result.saved.id,
                                projectTitle = result.saved.title,
                                projectCreatedAtEpochMs = result.saved.createdAtEpochMs,
                                goalText = result.saved.goalText,
                                clarificationValues = result.saved.clarificationValues,
                                pendingQuestions = result.resolution.missing,
                                completedConnectionIds = result.saved.completedConnectionIds,
                                currentBuildStepIndex = result.saved.currentBuildStepIndex,
                                deployed = result.saved.deployed,
                                screen = AppScreen.HOME,
                                lastSavedAtEpochMs = result.saved.updatedAtEpochMs,
                            )
                        }
                        maybeStartBridgeSync()
                    }

                    is ResumeResult.NeedsCompileInput -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                projectId = result.saved.id,
                                projectTitle = result.saved.title,
                                projectCreatedAtEpochMs = result.saved.createdAtEpochMs,
                                goalText = result.saved.goalText,
                                clarificationValues = result.saved.clarificationValues,
                                pendingQuestions = result.questions,
                                completedConnectionIds = result.saved.completedConnectionIds,
                                currentBuildStepIndex = result.saved.currentBuildStepIndex,
                                deployed = result.saved.deployed,
                                screen = AppScreen.HOME,
                                lastSavedAtEpochMs = result.saved.updatedAtEpochMs,
                            )
                        }
                        maybeStartBridgeSync()
                    }

                    is ResumeResult.Error -> {
                        _state.update {
                            it.copy(
                                busy = false,
                                error = result.message,
                            )
                        }
                    }
                }
            }.onFailure { throwable ->
                _state.update {
                    it.copy(
                        busy = false,
                        error = throwable.message
                            ?: "保存したプロジェクトを開けませんでした。",
                    )
                }
            }
        }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch {
            bridgeCredentialStore?.clear(projectId)
            val savedProjects = withContext(Dispatchers.IO) {
                projectRepository.delete(projectId)
                projectRepository.list()
            }

            if (_state.value.projectId == projectId) {
                bridgeJob?.cancel()
                bridgeJob = null
                _state.value = BuilderAppState(savedProjects = savedProjects)
            } else {
                _state.update { it.copy(savedProjects = savedProjects) }
            }
        }
    }

    fun connect(context: Context) {
        if (_state.value.busy) return

        recordFriction { recordDeviceConnectAction() }

        _state.update {
            it.copy(
                busy = true,
                error = null,
                deployMessage = "装置を探しています…",
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    AndroidBleRuntimeConnector(context.applicationContext)
                        .scanAndConnect()
                        .getOrThrow()
                }
            }.onSuccess { connection ->
                _state.update {
                    it.copy(
                        busy = false,
                        connection = connection,
                        deployMessage = "装置に接続しました。",
                    )
                }
                maybeStartBridgeSync()
            }.onFailure {
                _state.update {
                    it.copy(
                        busy = false,
                        error = BeginnerErrorPresenter.connectionFailure(),
                        deployMessage = "",
                    )
                }
            }
        }
    }

    fun deploy() {
        val current = _state.value
        val bundle = current.bundle ?: return
        val connection = current.connection ?: run {
            _state.update { it.copy(error = "先に装置へ接続してください。") }
            return
        }
        val manifest = bundle.manifest ?: run {
            _state.update { it.copy(error = "装置設定データがありません。") }
            return
        }

        if (current.busy) return

        recordFriction { recordDeployAction() }

        _state.update {
            it.copy(
                busy = true,
                error = null,
                deployProgress = 5,
                deployMessage = "装置を確認しています…",
            )
        }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val client = RuntimeClient(connection.transport)
                    updateDeploy(35, "設計データを送っています…")
                    client.deployManifest(
                        requestId = "app-deploy",
                        manifestVersion = manifest.version,
                        payload = CanonicalManifestEncoder().encode(manifest),
                        projectId = manifest.projectId,
                    ).getOrThrow()

                    updateDeploy(65, "設定を確認しています…")
                    client.verifyProject(
                        requestId = "app-verify",
                        projectId = manifest.projectId,
                    ).getOrThrow()

                    val requiredTests = bundle.testPlan.tests.filter { it.required }
                    requiredTests.forEachIndexed { index, test ->
                        val base = 70
                        val span = 25
                        val progress =
                            base + ((index + 1) * span / requiredTests.size.coerceAtLeast(1))
                        updateDeploy(progress, test.name + "を確認しています…")
                        client.runTest(
                            requestId = "app-test-" + test.id,
                            testId = test.id,
                        ).getOrThrow()
                    }
                }
            }.onSuccess {
                val from = _state.value.screen
                recordFriction {
                    recordScreenTransition(
                        from = from.name,
                        to = AppScreen.CONTROL.name,
                        userInitiated = false,
                    )
                }
                _state.update {
                    it.copy(
                        busy = false,
                        deployed = true,
                        deployProgress = 100,
                        deployMessage = "設定が完了しました。",
                        screen = AppScreen.CONTROL,
                    )
                }
                persistCurrent()
            }.onFailure {
                _state.update {
                    it.copy(
                        busy = false,
                        error = BeginnerErrorPresenter.deploymentFailure(),
                        deployMessage = "要確認",
                    )
                }
            }
        }
    }

    fun clearError() {
        _state.update { it.copy(error = null) }
    }

    private fun maybeStartBridgeSync() {
        val projectId = _state.value.projectId ?: return
        val credentials = bridgeCredentialStore?.load(projectId) ?: return
        startBridgeSync(credentials)
    }

    private fun startBridgeSync(credentials: Base44BridgeCredentials) {
        val bridge = base44BridgeClient ?: return
        bridgeJob?.cancel()

        bridgeJob = viewModelScope.launch(Dispatchers.IO) {
            var acknowledgements = emptyList<Base44BridgeAck>()

            while (isActive) {
                val snapshot = _state.value
                val bundle = snapshot.bundle
                val contract = bundle?.let {
                    VisualAppLayoutResolver.apply(
                        graph = it.projectGraph,
                        contract =
                            it.softwarePlan.base44Handoff?.integration,
                        positions = snapshot.graphNodePositions,
                    )
                }
                val connection = snapshot.connection

                if (connection == null || bundle == null) {
                    runCatching {
                        bridge.sync(
                            credentials = credentials,
                            telemetry = emptyMap(),
                            settings = emptyMap(),
                            contract = contract,
                            hardwareConnected = false,
                            acknowledgements = acknowledgements,
                        )
                    }.onSuccess { sync ->
                        acknowledgements = emptyList()
                        maybeApplyBase44Handoff(
                            credentials = credentials,
                            handoff = sync.designHandoff,
                        )
                        _state.update {
                            it.copy(
                                base44BridgeOnline = true,
                                base44BridgeStatus =
                                    if (bundle == null) {
                                        "CircuitFlowと確定仕様を同期中です。"
                                    } else {
                                        "CircuitFlowと画面構成を同期中。実機は未接続です。"
                                    },
                                bridgeLastSyncAtEpochMs =
                                    System.currentTimeMillis(),
                            )
                        }
                    }.onFailure { throwable ->
                        _state.update {
                            it.copy(
                                base44BridgeOnline = false,
                                base44BridgeStatus =
                                    "CircuitFlow同期エラー: " +
                                        (throwable.message ?: "通信失敗"),
                            )
                        }
                    }

                    delay(BRIDGE_SYNC_INTERVAL_MS)
                    continue
                }

                runCatching {
                    val runtime = RuntimeControlClient(connection.transport)
                    val telemetry = runtime.telemetry()
                    val settings = runtime.loadSettings(bundle.uiSpec)
                    bridge.sync(
                        credentials = credentials,
                        telemetry = telemetry,
                        settings = settings,
                        contract = contract,
                        hardwareConnected = true,
                        acknowledgements = acknowledgements,
                    )
                }.onSuccess { sync ->
                    maybeApplyBase44Handoff(
                        credentials = credentials,
                        handoff = sync.designHandoff,
                    )

                    if (sync.designHandoff == null) {
                        val runtime =
                            RuntimeControlClient(connection.transport)
                        acknowledgements = sync.commands.map { command ->
                            executeBridgeCommand(
                                runtime = runtime,
                                command = command,
                                contract = contract,
                            )
                        }
                    } else {
                        acknowledgements = emptyList()
                    }

                    _state.update {
                        it.copy(
                            base44BridgeOnline = true,
                            base44BridgeStatus =
                                if (sync.designHandoff == null) {
                                    "CircuitFlowと実機データを同期中"
                                } else {
                                    "Base44の更新仕様を受信しました。再設計しています。"
                                },
                            bridgeLastSyncAtEpochMs =
                                System.currentTimeMillis(),
                        )
                    }
                }.onFailure { throwable ->
                    acknowledgements = emptyList()
                    _state.update {
                        it.copy(
                            base44BridgeOnline = false,
                            base44BridgeStatus =
                                "CircuitFlow同期エラー: " +
                                    (throwable.message ?: "通信失敗"),
                        )
                    }
                }

                delay(BRIDGE_SYNC_INTERVAL_MS)
            }
        }
    }

    private fun maybeApplyBase44Handoff(
        credentials: Base44BridgeCredentials,
        handoff: Base44DesignHandoff?,
    ) {
        handoff ?: return
        val current = _state.value
        if (
            current.base44HandoffRevision == handoff.revision &&
            current.base44HandoffStatus in
                setOf("processing", "needs_input", "compiled")
        ) {
            return
        }
        if (current.busy) return

        val localProjectId = current.projectId ?: return
        bridgeCredentialStore?.save(localProjectId, credentials)

        viewModelScope.launch {
            _state.update {
                val preserveClarifications =
                    handoff.status == "needs_input" &&
                        it.bundle == null &&
                        it.goalText == handoff.goalText
                it.copy(
                    goalText = handoff.goalText,
                    projectTitle = handoff.title,
                    clarificationValues =
                        if (preserveClarifications) {
                            it.clarificationValues
                        } else {
                            emptyMap()
                        },
                    pendingQuestions = emptyList(),
                    base44HandoffRevision = handoff.revision,
                    base44HandoffStatus =
                        if (handoff.status == "needs_input") {
                            "needs_input"
                        } else {
                            "processing"
                        },
                    base44HandoffMessage =
                        if (handoff.status == "needs_input") {
                            "Base44仕様の追加確認を再開します。"
                        } else {
                            "Base44の確定仕様 revision " +
                                handoff.revision +
                                " をProject Compilerで再検証しています。"
                        },
                    error = null,
                )
            }
            persistCurrent()
            resolveAndCompile()
        }
    }

    private fun acknowledgeActiveBase44Handoff(
        status: String,
        message: String,
    ) {
        val bridge = base44BridgeClient ?: return
        val snapshot = _state.value
        val revision = snapshot.base44HandoffRevision ?: return
        val localProjectId = snapshot.projectId ?: return
        val credentials =
            bridgeCredentialStore?.load(localProjectId) ?: return
        val bundle = snapshot.bundle
        val contract = bundle?.let {
            VisualAppLayoutResolver.apply(
                graph = it.projectGraph,
                contract =
                    it.softwarePlan.base44Handoff?.integration,
                positions = snapshot.graphNodePositions,
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                bridge.sync(
                    credentials = credentials,
                    telemetry = emptyMap(),
                    settings = emptyMap(),
                    contract = contract,
                    hardwareConnected = false,
                    handoffAck = Base44DesignHandoffAck(
                        revision = revision,
                        status = status,
                        message = message,
                        localProjectId = localProjectId,
                    ),
                )
            }
        }
    }

    private fun executeBridgeCommand(
        runtime: RuntimeControlClient,
        command: Base44BridgeCommand,
        contract: AppHardwareIntegrationContract?,
    ): Base44BridgeAck =
        runCatching {
            val allowed = contract
                ?.channels
                ?.any {
                    it.binding == command.binding &&
                        it.direction == AppBridgeDirection.BASE44_TO_HARDWARE
                } == true

            require(allowed) {
                "Project ContractにないBridgeコマンドを拒否しました。"
            }

            when {
                command.binding.startsWith("settings.") -> {
                    val settingId = command.binding.removePrefix("settings.")
                    runtime.setSetting(settingId, command.value)
                }

                else -> error(
                    "未対応のBridge bindingです: " + command.binding
                )
            }
        }.fold(
            onSuccess = {
                Base44BridgeAck(
                    commandId = command.commandId,
                    ok = true,
                )
            },
            onFailure = { throwable ->
                Base44BridgeAck(
                    commandId = command.commandId,
                    ok = false,
                    error = throwable.message ?: "runtime_command_failed",
                )
            },
        )

    private fun runComponentResearch(
        requests: List<ComponentResearchRequest>,
        retry: () -> Unit,
    ) {
        val client = componentResearchClient
        val store = componentResearchStore

        if (client == null || store == null) {
            _state.update {
                it.copy(
                    busy = false,
                    componentResearchActive = false,
                    componentResearchMessage =
                        "未登録部品を検出しましたが、" +
                            "Component Research Gatewayが設定されていません。",
                    error =
                        "未登録部品: " +
                            requests.joinToString {
                                request ->
                                request.requested.rawName
                            },
                )
            }
            return
        }

        _state.update {
            it.copy(
                busy = true,
                componentResearchActive = true,
                componentResearchMessage =
                    "未登録部品を公式資料から調査しています: " +
                        requests.joinToString {
                            request ->
                            request.requested.rawName
                        },
                componentResearchRecords = emptyList(),
                error = null,
                screen = AppScreen.HOME,
            )
        }

        viewModelScope.launch {
            val records =
                withContext(Dispatchers.IO) {
                    requests.map { request ->
                        val record =
                            client.research(request)
                                .getOrElse { throwable ->
                                    ComponentResearchRecord(
                                        requestId =
                                            request.requestId,
                                        requestedName =
                                            request.requested.rawName,
                                        status =
                                            ComponentVerificationStatus
                                                .DISCOVERED,
                                        notes = listOf(
                                            "Research失敗: " +
                                                (
                                                    throwable.message
                                                        ?: "unknown"
                                                )
                                        ),
                                        researchedAtEpochMs =
                                            System.currentTimeMillis(),
                                    )
                                }
                        store.saveResearchRecord(record)
                        record
                    }
                }

            val allReady =
                records.isNotEmpty() &&
                    records.all {
                        it.status ==
                            ComponentVerificationStatus.DESIGN_READY
                    }

            _state.update {
                it.copy(
                    busy = false,
                    componentResearchActive = false,
                    componentResearchRecords = records,
                    componentResearchMessage =
                        if (allReady) {
                            "部品調査・検証・Catalog登録が完了しました。" +
                                "設計を再実行します。"
                        } else {
                            "部品調査は完了しましたが、" +
                                "安全な自動採用に必要な情報が不足しています。"
                        },
                )
            }

            if (allReady) {
                retry()
            } else {
                val details =
                    records
                        .filter {
                            it.status !=
                                ComponentVerificationStatus.DESIGN_READY
                        }
                        .joinToString("\n") { record ->
                            record.requestedName +
                                ": " +
                                (
                                    record.missingFields
                                        .joinToString()
                                        .ifBlank {
                                            record.status.name
                                        }
                                )
                        }
                _state.update {
                    it.copy(
                        error =
                            if (details.isBlank()) {
                                null
                            } else {
                                "検証待ち部品:\n" + details
                            },
                    )
                }

                val activeHandoff =
                    _state.value.base44HandoffRevision != null
                if (activeHandoff) {
                    acknowledgeActiveBase44Handoff(
                        status = "needs_input",
                        message =
                            "未登録部品のResearchは完了しましたが、" +
                                "設計利用に必要な検証が残っています。",
                    )
                }
            }
        }
    }

    private fun resolveAndCompile() {
        val current = _state.value
        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.Default) {
                    val intent = interpreter.interpret(
                        text = current.goalText,
                        clarifications = current.clarificationValues,
                    )
                    when (val resolution = engine.resolve(intent)) {
                        is RequirementResolution.NeedUserInput ->
                            ResolutionResult.Questions(resolution.missing)

                        is RequirementResolution.Ready -> {
                            when (val compile = engine.compile(resolution.requirements)) {
                                is CompileResult.Success ->
                                    ResolutionResult.Success(
                                        resolution.requirements,
                                        compile.bundle,
                                    )

                                is CompileResult.NeedUserInput ->
                                    ResolutionResult.Questions(compile.questions)

                                is CompileResult.NeedsComponentResearch ->
                                    ResolutionResult.Research(
                                        compile.requests
                                    )

                                is CompileResult.Blocked ->
                                    ResolutionResult.Error(
                                        BeginnerErrorPresenter.validationBlocked(
                                            compile.report
                                        )
                                    )

                                is CompileResult.Failed ->
                                    ResolutionResult.Error(
                                        BeginnerErrorPresenter.compileFailure(
                                            compile.error
                                        )
                                    )
                            }
                        }
                    }
                }
            }.onSuccess { result ->
                when (result) {
                    is ResolutionResult.Questions -> {
                        result.questions.firstOrNull()?.let { question ->
                            recordFriction {
                                recordQuestionPresented(question.slotId)
                            }
                        }
                        val activeHandoff =
                            current.base44HandoffRevision != null &&
                                current.base44HandoffStatus in
                                    setOf("processing", "needs_input")
                        val questionText =
                            result.questions.firstOrNull()?.userQuestion
                                ?: "追加確認が必要です。"
                        _state.update {
                            it.copy(
                                busy = false,
                                pendingQuestions = result.questions,
                                screen = AppScreen.HOME,
                                base44HandoffStatus =
                                    if (activeHandoff) {
                                        "needs_input"
                                    } else {
                                        it.base44HandoffStatus
                                    },
                                base44HandoffMessage =
                                    if (activeHandoff) {
                                        "Android側で追加確認が必要です: " +
                                            questionText
                                    } else {
                                        it.base44HandoffMessage
                                    },
                            )
                        }
                        persistCurrent()
                        if (activeHandoff) {
                            acknowledgeActiveBase44Handoff(
                                status = "needs_input",
                                message =
                                    "追加確認が必要です: " + questionText,
                            )
                        }
                    }

                    is ResolutionResult.Success -> {
                        val previous = _state.value
                        val activeHandoff =
                            current.base44HandoffRevision != null &&
                                current.base44HandoffStatus in
                                    setOf("processing", "needs_input")
                        val now = System.currentTimeMillis()
                        val projectId =
                            previous.projectId ?: UUID.randomUUID().toString()
                        val createdAt =
                            previous.projectCreatedAtEpochMs ?: now
                        val currentConnectionIds =
                            result.bundle.circuitGraph.connections
                                .map { it.id }
                                .toSet()
                        val preservedCompleted =
                            previous.completedConnectionIds
                                .intersect(currentConnectionIds)
                        val validGraphNodeIds =
                            result.bundle.projectGraph.nodes
                                .map { it.id }
                                .toSet()

                        val from = previous.screen
                        recordFriction {
                            recordScreenTransition(
                                from = from.name,
                                to = AppScreen.DESIGN.name,
                                userInitiated = false,
                            )
                        }

                        _state.update {
                            it.copy(
                                busy = false,
                                pendingQuestions = emptyList(),
                                requirements = result.requirements,
                                bundle = result.bundle,
                                screen = AppScreen.DESIGN,
                                projectId = projectId,
                                projectTitle =
                                    if (activeHandoff) {
                                        it.projectTitle
                                            ?: ProjectTitle.fromGoal(it.goalText)
                                    } else {
                                        ProjectTitle.fromGoal(it.goalText)
                                    },
                                projectCreatedAtEpochMs = createdAt,
                                completedConnectionIds = preservedCompleted,
                                currentBuildStepIndex =
                                    it.currentBuildStepIndex.coerceIn(
                                        0,
                                        (result.bundle.diagramSpec.buildPlan
                                            ?.steps
                                            ?.lastIndex
                                            ?: 0).coerceAtLeast(0),
                                    ),
                                graphNodePositions =
                                    it.graphNodePositions.filterKeys { nodeId ->
                                        nodeId in validGraphNodeIds
                                    },
                                deployed =
                                    if (activeHandoff) false else it.deployed,
                                connection =
                                    if (activeHandoff) null else it.connection,
                                deployProgress =
                                    if (activeHandoff) 0 else it.deployProgress,
                                deployMessage =
                                    if (activeHandoff) {
                                        "Base44の確定仕様から設計を更新しました。" +
                                            "実機への配備前に内容を確認してください。"
                                    } else {
                                        it.deployMessage
                                    },
                                base44HandoffStatus =
                                    if (activeHandoff) {
                                        "compiled"
                                    } else {
                                        it.base44HandoffStatus
                                    },
                                base44HandoffMessage =
                                    if (activeHandoff) {
                                        "設計・電気安全検証・Firmware生成が完了しました。"
                                    } else {
                                        it.base44HandoffMessage
                                    },
                            )
                        }
                        persistCurrent()
                        if (activeHandoff) {
                            acknowledgeActiveBase44Handoff(
                                status = "compiled",
                                message =
                                    "設計・電気安全検証・Firmware生成が完了しました。",
                            )
                        }
                        maybeStartBridgeSync()
                    }

                    is ResolutionResult.Error -> {
                        val activeHandoff =
                            current.base44HandoffRevision != null &&
                                current.base44HandoffStatus in
                                    setOf("processing", "needs_input")
                        _state.update {
                            it.copy(
                                busy = false,
                                error = result.message,
                                base44HandoffStatus =
                                    if (activeHandoff) {
                                        "failed"
                                    } else {
                                        it.base44HandoffStatus
                                    },
                                base44HandoffMessage =
                                    if (activeHandoff) {
                                        result.message
                                    } else {
                                        it.base44HandoffMessage
                                    },
                            )
                        }
                        persistCurrent()
                        if (activeHandoff) {
                            acknowledgeActiveBase44Handoff(
                                status = "failed",
                                message = result.message,
                            )
                        }
                    }
                }
            }.onFailure { throwable ->
                val message =
                    throwable.message ?: "設計処理に失敗しました。"
                val activeHandoff =
                    current.base44HandoffRevision != null &&
                        current.base44HandoffStatus in
                            setOf("processing", "needs_input")
                _state.update {
                    it.copy(
                        busy = false,
                        error = message,
                        base44HandoffStatus =
                            if (activeHandoff) {
                                "failed"
                            } else {
                                it.base44HandoffStatus
                            },
                        base44HandoffMessage =
                            if (activeHandoff) {
                                message
                            } else {
                                it.base44HandoffMessage
                            },
                    )
                }
                persistCurrent()
                if (activeHandoff) {
                    acknowledgeActiveBase44Handoff(
                        status = "failed",
                        message = message,
                    )
                }
            }
        }
    }

    private fun restoreReadyProject(result: ResumeResult.Ready) {
        val validConnections =
            result.bundle.circuitGraph.connections.map { it.id }.toSet()
        val completed =
            result.saved.completedConnectionIds.intersect(validConnections)
        val lastIndex =
            (result.bundle.diagramSpec.buildPlan?.steps?.lastIndex ?: 0)
                .coerceAtLeast(0)
        val restoredScreen = runCatching {
            AppScreen.valueOf(result.saved.lastScreen)
        }.getOrDefault(AppScreen.DESIGN).let { savedScreen ->
            when (savedScreen) {
                AppScreen.CONTROL -> AppScreen.CONNECT
                AppScreen.REVISION -> AppScreen.DESIGN
                else -> savedScreen
            }
        }

        _state.update {
            it.copy(
                busy = false,
                error = null,
                projectId = result.saved.id,
                projectTitle = result.saved.title,
                projectCreatedAtEpochMs = result.saved.createdAtEpochMs,
                goalText = result.saved.goalText,
                clarificationValues = result.saved.clarificationValues,
                pendingQuestions = emptyList(),
                requirements = result.requirements,
                bundle = result.bundle,
                completedConnectionIds = completed,
                currentBuildStepIndex =
                    result.saved.currentBuildStepIndex.coerceIn(0, lastIndex),
                deployed = result.saved.deployed,
                connection = null,
                deployProgress = 0,
                deployMessage =
                    if (result.saved.deployed) {
                        "前回の設定済みプロジェクトです。装置へ再接続してください。"
                    } else {
                        ""
                    },
                screen = restoredScreen,
                lastSavedAtEpochMs = result.saved.updatedAtEpochMs,
                graphNodePositions = result.saved.graphNodePositions.filterKeys { nodeId ->
                    result.bundle.projectGraph.nodes.any { it.id == nodeId }
                },
                graphLayoutUndoStack = emptyList(),
                graphLayoutRedoStack = emptyList(),
                selectedGraphNodeId = null,
            )
        }
    }

    private fun resolveRevisionCandidate(
        goal: String,
        clarifications: Map<String, String>,
    ): ResolutionResult {
        val intent = interpreter.interpret(
            text = goal,
            clarifications = clarifications,
        )
        return when (val resolution = engine.resolve(intent)) {
            is RequirementResolution.NeedUserInput ->
                ResolutionResult.Questions(resolution.missing)

            is RequirementResolution.Ready ->
                when (val compile = engine.compile(resolution.requirements)) {
                    is CompileResult.Success ->
                        ResolutionResult.Success(
                            resolution.requirements,
                            compile.bundle,
                        )

                    is CompileResult.NeedUserInput ->
                        ResolutionResult.Questions(compile.questions)

                    is CompileResult.Blocked ->
                        ResolutionResult.Error(
                            BeginnerErrorPresenter.validationBlocked(compile.report)
                        )

                    is CompileResult.Failed ->
                        ResolutionResult.Error(
                            BeginnerErrorPresenter.compileFailure(compile.error)
                        )
                }
        }
    }

    private fun commitRevision(
        updatedGoal: String,
        clarifications: Map<String, String>,
        requirements: ResolvedRequirements,
        bundle: ReleaseBundle,
    ) {
        bridgeJob?.cancel()
        bridgeJob = null
        val previous = _state.value
        val currentConnectionIds =
            bundle.circuitGraph.connections.map { it.id }.toSet()
        val preservedCompleted =
            previous.completedConnectionIds.intersect(currentConnectionIds)
        val lastIndex =
            (bundle.diagramSpec.buildPlan?.steps?.lastIndex ?: 0).coerceAtLeast(0)
        val returnScreen = previous.revisionReturnScreen ?: AppScreen.DESIGN
        val validGraphNodeIds = bundle.projectGraph.nodes.map { it.id }.toSet()
        val preservedGraphPositions =
            previous.graphNodePositions.filterKeys { it in validGraphNodeIds }

        recordFriction {
            recordScreenTransition(
                from = previous.screen.name,
                to = returnScreen.name,
                userInitiated = false,
            )
        }

        _state.update {
            it.copy(
                busy = false,
                goalText = updatedGoal,
                clarificationValues = clarifications,
                pendingQuestions = emptyList(),
                requirements = requirements,
                bundle = bundle,
                screen = returnScreen,
                deployed = false,
                connection = null,
                deployProgress = 0,
                deployMessage = "設計を更新したため、装置への再設定が必要です。",
                completedConnectionIds = preservedCompleted,
                currentBuildStepIndex =
                    it.currentBuildStepIndex.coerceIn(0, lastIndex),
                revisionStatusMessage =
                    "追加要望を対話で確定し、安全確認を通して設計を更新しました。",
                revisionMessages = emptyList(),
                revisionCandidateGoalText = "",
                revisionClarificationValues = emptyMap(),
                revisionPendingSlotId = null,
                additionalRequestText = "",
                graphNodePositions = preservedGraphPositions,
                graphLayoutUndoStack = emptyList(),
                graphLayoutRedoStack = emptyList(),
                selectedGraphNodeId =
                    it.selectedGraphNodeId?.takeIf { nodeId ->
                        nodeId in validGraphNodeIds
                    },
                revisionReturnScreen = null,
                error = null,
            )
        }
        persistCurrent()
    }

    private fun assistantMessage(text: String): RevisionChatMessage =
        RevisionChatMessage(
            id = UUID.randomUUID().toString(),
            speaker = RevisionSpeaker.ASSISTANT,
            text = text,
        )

    private fun persistCurrent() {
        val snapshotState = _state.value
        val projectId = snapshotState.projectId ?: return
        if (snapshotState.goalText.isBlank()) return

        val now = System.currentTimeMillis()
        val project = SavedProject(
            id = projectId,
            title = snapshotState.projectTitle
                ?: ProjectTitle.fromGoal(snapshotState.goalText),
            goalText = snapshotState.goalText,
            clarificationValues = snapshotState.clarificationValues,
            completedConnectionIds = snapshotState.completedConnectionIds,
            currentBuildStepIndex = snapshotState.currentBuildStepIndex,
            lastScreen = snapshotState.screen.name,
            deployed = snapshotState.deployed,
            createdAtEpochMs =
                snapshotState.projectCreatedAtEpochMs ?: now,
            updatedAtEpochMs = now,
            graphNodePositions = snapshotState.graphNodePositions,
        )

        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                projectRepository.save(project)
            }
            _state.update {
                it.copy(
                    projectTitle = project.title,
                    projectCreatedAtEpochMs = project.createdAtEpochMs,
                    lastSavedAtEpochMs = now,
                )
            }
            refreshSavedProjects()
        }
    }

    private fun refreshSavedProjects() {
        viewModelScope.launch {
            val projects = withContext(Dispatchers.IO) {
                projectRepository.list()
            }
            _state.update { it.copy(savedProjects = projects) }
        }
    }

    private fun recordFriction(
        action: FrictionTelemetryRecorder.() -> Unit,
    ) {
        frictionTelemetry.action()
        _friction.value = frictionTelemetry.snapshot()
    }

    private fun updateDeploy(progress: Int, message: String) {
        _state.update {
            it.copy(
                deployProgress = progress,
                deployMessage = message,
            )
        }
    }

    class Factory(
        private val projectRepository: ProjectRepository,
        private val revisionAssistant: RevisionLanguageAssistant =
            LocalRevisionLanguageAssistant(),
        private val bridgeCredentialStore: Base44BridgeCredentialStore? = null,
        private val engine: ApplicationProjectEngine =
            ApplicationProjectEngine(),
        private val componentResearchClient: ComponentResearchClient? = null,
        private val componentResearchStore: ComponentResearchStore? = null,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(BuilderAppViewModel::class.java))
            return BuilderAppViewModel(
                projectRepository = projectRepository,
                revisionAssistant = revisionAssistant,
                bridgeCredentialStore = bridgeCredentialStore,
                engine = engine,
                componentResearchClient = componentResearchClient,
                componentResearchStore = componentResearchStore,
            ) as T
        }
    }

    private companion object {
        const val BRIDGE_SYNC_INTERVAL_MS = 3_000L
        const val MAX_GRAPH_LAYOUT_HISTORY = 30
    }

    private sealed interface ResolutionResult {
        data class Questions(
            val questions: List<com.aielectronics.core.model.MissingRequirement>,
        ) : ResolutionResult

        data class Research(
            val requests: List<ComponentResearchRequest>,
        ) : ResolutionResult

        data class Success(
            val requirements: ResolvedRequirements,
            val bundle: ReleaseBundle,
        ) : ResolutionResult

        data class Error(val message: String) : ResolutionResult
    }

    private sealed interface ResumeResult {
        data class Ready(
            val saved: SavedProject,
            val requirements: ResolvedRequirements,
            val bundle: ReleaseBundle,
        ) : ResumeResult

        data class NeedsInput(
            val saved: SavedProject,
            val resolution: RequirementResolution.NeedUserInput,
        ) : ResumeResult

        data class NeedsCompileInput(
            val saved: SavedProject,
            val questions: List<com.aielectronics.core.model.MissingRequirement>,
        ) : ResumeResult

        data class Research(
            val saved: SavedProject,
            val requests: List<ComponentResearchRequest>,
        ) : ResumeResult

        data class Error(val message: String) : ResumeResult
    }
}
